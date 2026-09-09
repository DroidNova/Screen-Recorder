package com.droidnova.screenrecorder.feature.recordings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.heading
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
    var renameSubmitting by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<Int?>(null) }
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
        if (activityResult.resultCode != Activity.RESULT_OK || operation == null || uri == null) {
            if (operation == ConsentOperation.Rename) renameSubmitting = false
            return@rememberLauncherForActivityResult
        }
        if (operation == ConsentOperation.Delete && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            RecordingThumbnailCache.remove(uri); refresh(); return@rememberLauncherForActivityResult
        }
        scope.launch {
            val modified = withContext(Dispatchers.IO) {
                if (operation == ConsentOperation.Rename && name != null) repository.rename(uri, name) else repository.delete(uri)
            }
            if (modified is ModificationResult.Success || operation == ConsentOperation.Delete && modified is ModificationResult.Missing) {
                RecordingThumbnailCache.remove(uri); refresh()
                if (operation == ConsentOperation.Rename) {
                    renameSubmitting = false
                    renameItem = null
                }
            } else {
                if (operation == ConsentOperation.Rename) renameSubmitting = false
                showMessage(context.getString(if (modified is ModificationResult.Missing) R.string.recording_unavailable_file else R.string.recording_action_failed))
            }
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
                ModificationResult.Success -> {
                    RecordingThumbnailCache.remove(item.contentUri); refresh()
                    if (operation == ConsentOperation.Rename) {
                        renameSubmitting = false
                        renameItem = null
                    }
                }
                ModificationResult.Missing -> {
                    RecordingThumbnailCache.remove(item.contentUri); refresh()
                    if (operation == ConsentOperation.Rename) {
                        renameSubmitting = false
                        showMessage(context.getString(R.string.recording_unavailable_file))
                    }
                }
                is ModificationResult.ConsentRequired -> launchConsent(operation, item, name, outcome.intentSender)
                ModificationResult.Failed -> {
                    renameSubmitting = false
                    showMessage(context.getString(R.string.recording_action_failed))
                }
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

    Column(modifier.fillMaxSize().widthIn(max = 720.dp).padding(top = Spacing.Standard)) {
        Text(stringResource(R.string.nav_recordings), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = Spacing.Page, vertical = Spacing.Small).semantics { heading() })
        when (val state = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LibraryResult.Error -> StateMessage(R.string.recordings_error, R.string.retry, { refresh() }, Modifier)
        LibraryResult.PermissionLimited -> StateMessage(R.string.legacy_permission_explanation, R.string.allow_access, {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }, Modifier)
        is LibraryResult.Success -> if (state.recordings.isEmpty()) EmptyRecordings(Modifier) else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.Page),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = Spacing.Standard, bottom = Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.Component),
        ) {
            items(state.recordings, key = { it.contentUri.toString() }) { item ->
                RecordingCard(item, { play(context, item.contentUri, showMessage) { refresh() } }, { share(context, item.contentUri, showMessage) },
                    { renameItem = item }, { deleteItem = item })
            }
        }
        }
    }

    renameItem?.let { item -> RenameDialog(
        item = item,
        submitting = renameSubmitting,
        externalError = renameError,
        dismiss = { if (!renameSubmitting) { renameItem = null; renameError = null } },
        confirm = { rawName ->
            normalizeUserRecordingName(rawName).fold(
                onSuccess = { name -> renameError = null; renameSubmitting = true; modify(ConsentOperation.Rename, item, name) },
                onFailure = { renameError = R.string.invalid_recording_name },
            )
        },
    ) }
    deleteItem?.let { item -> AlertDialog(
        onDismissRequest = { deleteItem = null }, title = { Text(stringResource(R.string.delete_recording)) },
        text = { Column { Text(stringResource(R.string.delete_recording_confirmation, item.displayName)); Text(stringResource(R.string.recording_removed_detail), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        confirmButton = { TextButton(onClick = { deleteItem = null; modify(ConsentOperation.Delete, item) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
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
    var launching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp).clickable(onClickLabel = stringResource(R.string.play_recording)) {
        if (!launching) {
            launching = true
            play()
            scope.launch { delay(750); launching = false }
        }
    }) {
        Row(Modifier.padding(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(item, Modifier.width(124.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(start = Spacing.Component), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(DateFormat.getMediumDateFormat(LocalContext.current).format(Date(item.modifiedSeconds * 1000L)) + " · " +
                    DateFormat.getTimeFormat(LocalContext.current).format(Date(item.modifiedSeconds * 1000L)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatter.formatFileSize(LocalContext.current, item.sizeBytes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                val actionsDescription = stringResource(R.string.recording_actions, item.displayName)
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = actionsDescription }) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.play)) }, { menu = false; play() })
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; rename() })
                    DropdownMenuItem({ Text(stringResource(R.string.share)) }, { menu = false; share() })
                    DropdownMenuItem({ Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, { menu = false; delete() })
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
        Text(formatDuration(item.durationMillis), Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.scrim.copy(alpha = .75f)).padding(4.dp), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable private fun RenameDialog(
    item: LibraryRecording,
    submitting: Boolean,
    externalError: Int?,
    dismiss: () -> Unit,
    confirm: (String) -> Unit,
) {
    var value by rememberSaveable(item.contentUri.toString()) { mutableStateOf(removeMp4Extension(item.displayName)) }
    val validation = normalizeUserRecordingName(value)
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.rename_recording)) },
        text = { OutlinedTextField(
            value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.filename)) },
            isError = validation.isFailure || externalError != null,
            supportingText = if (validation.isFailure || externalError != null) {
                { Text(stringResource(externalError ?: R.string.invalid_recording_name)) }
            } else null,
        ) },
        confirmButton = { TextButton({ confirm(value) }, enabled = validation.isSuccess && !submitting) { Text(stringResource(R.string.rename)) } },
        dismissButton = { TextButton(dismiss, enabled = !submitting) { Text(stringResource(R.string.cancel)) } })
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return if (seconds >= 3600L) "%02d:%02d:%02d".format(seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
    else "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

private const val MP4_EXTENSION = ".mp4"
private const val MAX_RECORDING_NAME_LENGTH = 80
private val REPEATED_MP4_EXTENSION = Regex("(?i)(\\.mp4)+$")

private fun removeMp4Extension(name: String): String = name.replace(Regex("(?i)\\.mp4$"), "")

private fun normalizeUserRecordingName(rawInput: String): Result<String> {
    val baseName = rawInput.trim().replace(REPEATED_MP4_EXTENSION, "").trim()
    return when {
        baseName.isBlank() -> Result.failure(IllegalArgumentException("Recording name cannot be empty"))
        baseName.length > MAX_RECORDING_NAME_LENGTH -> Result.failure(IllegalArgumentException("Recording name is too long"))
        baseName.any { it == '/' || it == '\\' || it.isISOControl() } -> Result.failure(IllegalArgumentException("Unsupported character"))
        else -> Result.success("$baseName$MP4_EXTENSION")
    }
}

private fun play(context: Context, uri: Uri, message: (String) -> Unit, onUnavailable: () -> Unit) {
    val activity = context.findActivity() ?: return message(context.getString(R.string.no_video_player))
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newUri(context.contentResolver, "recording", uri)
    try {
        activity.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        message(context.getString(R.string.no_video_player))
    } catch (_: SecurityException) {
        onUnavailable()
        message(context.getString(R.string.recording_unavailable_file))
    }
}

private fun share(context: Context, uri: Uri, message: (String) -> Unit) {
    val activity = context.findActivity() ?: return message(context.getString(R.string.unable_to_share))
    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newUri(context.contentResolver, "recording", uri)
    val chooser = Intent.createChooser(send, context.getString(R.string.share_recording))
    try {
        activity.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        message(context.getString(R.string.unable_to_share))
    } catch (_: SecurityException) {
        message(context.getString(R.string.unable_to_share))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
