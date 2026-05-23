package dev.anilbeesetti.nextplayer.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.anilbeesetti.nextplayer.core.data.newpipe.DownloadStatus
import dev.anilbeesetti.nextplayer.core.data.newpipe.DownloadTask
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateUp: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(NextIcons.ArrowDownward, contentDescription = "Add Download")
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active or queued downloads.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onCancel = { viewModel.cancel(task.id) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddDownloadDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { url, name, threads ->
                    viewModel.enqueue(
                        id = UUID.randomUUID().toString(),
                        title = name.ifBlank { "video_${System.currentTimeMillis()}.mp4" },
                        url = url,
                        threads = threads
                    )
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, fileName: String, threads: Int) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf(3f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Download") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Download URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Column {
                    Text("Threads: ${threads.toInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = threads,
                        onValueChange = { threads = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url, fileName, threads.toInt()) },
                enabled = url.isNotBlank()
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DownloadItemCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            val progress = if (task.totalBytes > 0) {
                task.downloadedBytes.toFloat() / task.totalBytes.toFloat()
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status: ${task.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    val dlMB = (task.downloadedBytes / (1024 * 1024f)).format(1)
                    val totalMB = if (task.totalBytes > 0) (task.totalBytes / (1024 * 1024f)).format(1) else "?"
                    Text(
                        text = "Size: $dlMB / $totalMB MB (${task.threadCount} threads)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.QUEUED) {
                        IconButton(onClick = onPause) {
                            Icon(NextIcons.ArrowDownward, contentDescription = "Pause")
                        }
                    } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                        IconButton(onClick = onResume) {
                            Icon(NextIcons.Play, contentDescription = "Resume")
                        }
                    }

                    if (task.status != DownloadStatus.COMPLETED && task.status != DownloadStatus.CANCELED) {
                        IconButton(onClick = onCancel) {
                            Icon(NextIcons.Close, contentDescription = "Cancel")
                        }
                    } else if (task.status == DownloadStatus.COMPLETED) {
                        Icon(NextIcons.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            val errorMsg = task.error
            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun Float.format(digits: Int): String = java.lang.String.format("%.${digits}f", this)
