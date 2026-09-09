package com.droidnova.screenrecorder.feature.recordings

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private enum class ConsentOperation { Rename, Delete }

@Composable
fun RecordingsScreen(
    showMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { RecordingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<LibraryResult?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var renameItem by remember { mutableStateOf<LibraryRecording?>(null) }
    var deleteItem by remember { mutableStateOf<LibraryRecording?>(null) }
    var pendingOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }

    fun hasLegacyPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun refresh(debounce: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            if (debounce) delay(250)
            val queried = withContext(Dispatchers.IO) { repository.query(hasLegacyPermission()) }
            result = queried
        }
    }

    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        val operation = pendingOperation?.let { runCatching { ConsentOperation.valueOf(it) }.getOrNull() }
        val uri = pendingUri?.let(Uri::parse)
        val name = pendingName
        pendingOperation = null; pendingUri = null; pendingName = null
        if (activityResult.resultCode != Activity.RESULT_OK || operation == null || uri == null) return@rememberLauncherForActivityResult
        if (operation == ConsentOperation.Delete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            RecordingThumbnailCache.remove(uri); refresh(); return@rememberLauncherForActivityResult
        }
        scope.launch {
            val modified = withContext(Dispatchers.IO) {
                if (operation == ConsentOperation.Rename && name != null) repository.rename(uri, name) else repository.delete(uri)
            }
            if (modified is ModificationResult.Success || modified is ModificationResult.Missing) {
                RecordingThumbnailCache.remove(uri); refresh()
            } else showMessage(context.getString(R.string.recording_action_failed))
        }
    }

    fun launchConsent(operation: ConsentOperation, item: LibraryRecording, name: String?, sender: android.content.IntentSender) {
        if (pendingOperation != null) return
        pendingOperation = operation.name; pendingUri = item.contentUri.toString(); pendingName = name
        consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    fun modify(operation: ConsentOperation, item: LibraryRecording, name: String? = null) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                if (operation == ConsentOperation.Rename) repository.rename(item.contentUri, requireNotNull(name))
                else repository.delete(item.contentUri)
            }
            when (outcome) {
                ModificationResult.Success, ModificationResult.Missing -> {
                    RecordingThumbnailCache.remove(item.contentUri); refresh()
                }
                is ModificationResult.ConsentRequired -> launchConsent(operation, item, name, outcome.intentSender)
                ModificationResult.Failed -> showMessage(context.getString(R.string.recording_action_failed))
            }
        }
    }

    DisposableEffect(lifecycleOwner, repository) {
        val observer = repository.observe { refresh(debounce = true) }
        val lifecycleObserver = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            refreshJob?.cancel(); repository.stopObserving(observer); lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
    LaunchedEffect(Unit) { refresh() }

    when (val state = result) {
        null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LibraryResult.Error -> StateMessage(R.string.recordings_error, R.string.retry, { refresh() }, modifier)
        LibraryResult.PermissionLimited -> StateMessage(R.string.legacy_permission_explanation, R.string.allow_access, {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }, modifier)
        is LibraryResult.Success -> if (state.recordings.isEmpty()) EmptyRecordings(modifier) else LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = Spacing.Page),
            verticalArrangement = Arrangement.spacedBy(Spacing.Component),
        ) {
            items(state.recordings, key = { it.contentUri.toString() }) { item ->
                RecordingCard(item, { play(context, item.contentUri, showMessage) }, { share(context, item.contentUri, showMessage) },
                    { renameItem = item }, { deleteItem = item })
            }
        }
    }

    renameItem?.let { item -> RenameDialog(item, { renameItem = null }, { name -> renameItem = null; modify(ConsentOperation.Rename, item, name) }) }
    deleteItem?.let { item -> AlertDialog(
        onDismissRequest = { deleteItem = null }, title = { Text(stringResource(R.string.delete_recording)) },
        text = { Text(stringResource(R.string.delete_recording_confirmation, item.displayName)) },
        confirmButton = { TextButton(onClick = { deleteItem = null; modify(ConsentOperation.Delete, item) }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { deleteItem = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
}

@Composable private fun EmptyRecordings(modifier: Modifier) = StateMessage(R.string.no_recordings_supporting, null, null, modifier)

@Composable private fun StateMessage(message: Int, action: Int?, onAction: (() -> Unit)?, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(Spacing.Page), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_recordings_outline), null, tint = MaterialTheme.colorScheme.primary)
        if (message == R.string.no_recordings_supporting) Text(stringResource(R.string.no_recordings), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.Small))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(stringResource(action)) }
    }
}

@Composable private fun RecordingCard(item: LibraryRecording, play: () -> Unit, share: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.play_recording), onClick = play)) {
        Row(Modifier.padding(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(item, Modifier.fillMaxWidth(0.42f).aspectRatio(16f / 9f))
            Column(Modifier.weight(1f).padding(start = Spacing.Component), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(DateFormat.getMediumDateFormat(LocalContext.current).format(Date(item.modifiedSeconds * 1000L)) + " · " +
                    DateFormat.getTimeFormat(LocalContext.current).format(Date(item.modifiedSeconds * 1000L)), style = MaterialTheme.typography.bodySmall)
                Text(Formatter.formatFileSize(LocalContext.current, item.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                val actionsDescription = stringResource(R.string.recording_actions, item.displayName)
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = actionsDescription }) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; rename() })
                    DropdownMenuItem({ Text(stringResource(R.string.share)) }, { menu = false; share() })
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; delete() })
                }
            }
        }
    }
}

@Composable private fun Thumbnail(item: LibraryRecording, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.contentUri) { mutableStateOf(RecordingThumbnailCache.get(item.contentUri)) }
    DisposableEffect(item.contentUri) {
        val signal = CancellationSignal()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            bitmap = withContext(Dispatchers.IO) { RecordingThumbnailCache.load(context.applicationContext, item.contentUri, signal) }
        }
        onDispose { signal.cancel(); job.cancel() }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(painterResource(R.drawable.ic_recordings_outline), null)
        Text(formatDuration(item.durationMillis), Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.scrim.copy(alpha = .75f)).padding(4.dp), color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable private fun RenameDialog(item: LibraryRecording, dismiss: () -> Unit, confirm: (String) -> Unit) {
    var value by rememberSaveable(item.contentUri.toString()) { mutableStateOf(removeMp4Extension(item.displayName)) }
    val trimmed = value.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 120 && trimmed.none { it == '/' || it == '\\' || it.isISOControl() }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.rename_recording)) },
        text = { OutlinedTextField(value, { value = it.take(120) }, singleLine = true, label = { Text(stringResource(R.string.filename)) }, isError = !valid) },
        confirmButton = { TextButton({
            val base = removeMp4Extension(trimmed)
            confirm("${if (base.startsWith("ScreenRecording_")) base else "ScreenRecording_$base"}.mp4")
        }, enabled = valid) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return if (seconds >= 3600L) "%02d:%02d:%02d".format(seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
    else "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private fun removeMp4Extension(name: String): String = if (name.endsWith(".mp4", ignoreCase = true)) name.dropLast(4) else name

private fun play(context: Context, uri: Uri, message: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .setClipData(ClipData.newUri(context.contentResolver, "recording", uri))
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent) else message(context.getString(R.string.no_video_player))
}

private fun share(context: Context, uri: Uri, message: (String) -> Unit) {
    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).setClipData(ClipData.newUri(context.contentResolver, "recording", uri))
    val chooser = Intent.createChooser(send, context.getString(R.string.share_recording))
    if (chooser.resolveActivity(context.packageManager) != null) context.startActivity(chooser) else message(context.getString(R.string.unable_to_share))
}
