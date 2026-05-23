package dev.anilbeesetti.nextplayer.core.data.newpipe

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED
}

data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val targetFile: File,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val threadCount: Int = 3,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null
)

@Singleton
class MediaDownloader @Inject constructor() {
    private val client = OkHttpClient.Builder().build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.map { it.values.toList() }.stateIn(scope, SharingStarted.Lazily, emptyList())
    
    private val activeJobs = ConcurrentHashMap<String, Job>()
    
    private val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mate3pipe")
    
    init {
        if (!downloadDir.exists()) downloadDir.mkdirs()
    }
    
    fun enqueue(id: String, title: String, url: String, threads: Int = 3) {
        val file = File(downloadDir, title)
        val task = DownloadTask(id, title, url, file, threadCount = threads)
        _tasks.update { it + (id to task) }
        startNext()
    }
    
    private fun startNext() {
        val pending = _tasks.value.values.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return
        if (activeJobs.size >= 2) return // Max 2 concurrent downloads
        
        val job = scope.launch {
            downloadProcess(pending)
        }
        activeJobs[pending.id] = job
    }
    
    private suspend fun downloadProcess(task: DownloadTask) {
        _tasks.update { it + (task.id to task.copy(status = DownloadStatus.DOWNLOADING)) }
        try {
            // 1. Get length
            val request = Request.Builder().url(task.url).head().build()
            val totalLength = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    response.header("Content-Length")?.toLongOrNull() ?: -1L
                }
            }
            
            val activeTask = itWillUpdateTotal(task, totalLength)
            if (totalLength <= 0) {
                singleThreadDownload(activeTask)
            } else {
                multiThreadDownload(activeTask)
            }
            _tasks.update { 
                val current = it[task.id] ?: return@update it
                it + (task.id to current.copy(status = DownloadStatus.COMPLETED, downloadedBytes = current.totalBytes)) 
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                // Handled in pause/cancel
            } else {
                _tasks.update { it + (task.id to (it[task.id] ?: task).copy(status = DownloadStatus.FAILED, error = e.message)) }
            }
        } finally {
            activeJobs.remove(task.id)
            startNext()
        }
    }
    
    private fun itWillUpdateTotal(task: DownloadTask, length: Long): DownloadTask {
        var updated = task
        _tasks.update { 
            val curr = it[task.id] ?: return@update it
            updated = curr.copy(totalBytes = length)
            it + (task.id to updated)
        }
        return updated
    }
    
    private suspend fun singleThreadDownload(task: DownloadTask) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(task.url).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw Exception("Empty body")
            var downloaded = 0L
            task.targetFile.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inp.read(buffer).also { read = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        out.write(buffer, 0, read)
                        downloaded += read
                        updateProgress(task.id, downloaded)
                    }
                }
            }
        }
    }
    
    private suspend fun multiThreadDownload(task: DownloadTask) = coroutineScope {
        val partSize = task.totalBytes / task.threadCount
        val downloaded = AtomicLong(0)
        
        // Ensure file size
        RandomAccessFile(task.targetFile, "rw").use { it.setLength(task.totalBytes) }
        
        val threads = (0 until task.threadCount).map { i ->
            async(Dispatchers.IO) {
                val start = i * partSize
                val end = if (i == task.threadCount - 1) task.totalBytes - 1 else (start + partSize - 1)
                
                downloadChunk(task, start, end) { bytes ->
                    val totalDL = downloaded.addAndGet(bytes.toLong())
                    updateProgress(task.id, totalDL)
                }
            }
        }
        threads.awaitAll()
    }
    
    private fun downloadChunk(task: DownloadTask, start: Long, end: Long, onProgress: (Int) -> Unit) {
        val request = Request.Builder()
            .url(task.url)
            .header("Range", "bytes=$start-$end")
            .get()
            .build()
            
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Chunk download failed: ${response.code}")
            val body = response.body ?: throw Exception("Empty body in chunk")
            
            RandomAccessFile(task.targetFile, "rw").use { raf ->
                raf.seek(start)
                body.byteStream().use { inp ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inp.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        onProgress(read)
                    }
                }
            }
        }
    }
    
    private var lastUpdate = 0L
    private fun updateProgress(id: String, bytes: Long) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 500) { // Limit UI updates to save recompositions
            lastUpdate = now
            val current = _tasks.value[id] ?: return
            _tasks.update { it + (id to current.copy(downloadedBytes = bytes)) }
        }
    }
    
    fun pause(id: String) {
        activeJobs[id]?.cancel()
        val current = _tasks.value[id] ?: return
        _tasks.update { it + (id to current.copy(status = DownloadStatus.PAUSED)) }
        activeJobs.remove(id)
        startNext()
    }
    
    fun resume(id: String) {
        val current = _tasks.value[id] ?: return
        _tasks.update { it + (id to current.copy(status = DownloadStatus.QUEUED)) }
        startNext()
    }
    
    fun cancel(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val current = _tasks.value[id] ?: return
        _tasks.update { it + (id to current.copy(status = DownloadStatus.CANCELED)) }
        startNext()
    }
}
