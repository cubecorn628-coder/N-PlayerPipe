package dev.anilbeesetti.nextplayer.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.data.newpipe.DownloadTask
import dev.anilbeesetti.nextplayer.core.data.newpipe.MediaDownloader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val mediaDownloader: MediaDownloader
) : ViewModel() {

    val tasks: StateFlow<List<DownloadTask>> = mediaDownloader.tasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enqueue(id: String, title: String, url: String, threads: Int) {
        mediaDownloader.enqueue(id, title, url, threads)
    }

    fun pause(id: String) {
        mediaDownloader.pause(id)
    }

    fun resume(id: String) {
        mediaDownloader.resume(id)
    }

    fun cancel(id: String) {
        mediaDownloader.cancel(id)
    }
}
