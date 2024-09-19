package com.droidnova.screenrecorder.ui.screens.recordingsscreen
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.droidnova.livelisten.extension.showToast
import com.droidnova.screenrecorder.MainViewModel
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.databinding.BottomSheetMoreOptionBinding
import com.droidnova.screenrecorder.databinding.DialogProgressBinding
import com.droidnova.screenrecorder.databinding.FragmentRecordingsScreenBinding
import com.droidnova.screenrecorder.utils.PermissionUtils
import com.droidnova.screenrecorder.utils.PreferenceUtil
import com.droidnova.screenrecorder.utils.VideoUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class RecordingsScreenFragment : Fragment() {
    private var binding: FragmentRecordingsScreenBinding? = null
    private val videoAdapter: VideoAdapter = VideoAdapter(getInteractionHandler())
    private val viewModel: MainViewModel by activityViewModels()

    private val folderAccessLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                context?.let {
                    it.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    PreferenceUtil.savedFolderUri = uri.toString()
                }
            } else {
                context?.showToast("Folder access not granted")
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enterTransition = MaterialFadeThrough()
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRecordingsScreenBinding.inflate(inflater, container, false)
        return binding?.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        viewModel.setupFileObserver()
        setupListeners()
        if (PreferenceUtil.showScopedStorageDialog){
            showSaveInfoDialog()
        }
    }

    private fun setupListeners() {
        // Observe recording events from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingEvents.collectLatest { event ->
                    when (event) {
                        is MainViewModel.RecordingEvent.ShowDeleteResultToast -> {
                            videoAdapter.clearSelection()
                            val message = if (event.deleted) {
                                "Video deleted successfully"
                            } else {
                                "Failed to delete video"
                            }
                            context?.showToast(message)
                        }
                        // Add other event types here as needed
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding?.rvVideos?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = videoAdapter
            hasFixedSize()
        }
        observeVideoListChanges()
    }

    private fun observeVideoListChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allVideos.observe(viewLifecycleOwner) { videosList ->
                Log.d("myTag", "Loaded videos: $videosList")
                if (videosList.isEmpty()){
                    binding?.tvNoVideos?.visibility = View.VISIBLE
                }else{
                    binding?.tvNoVideos?.visibility = View.GONE
                }
                videoAdapter.submitList(videosList)

            }
        }
    }

    private fun getInteractionHandler() = object : VideoAdapter.VideoItemClickListener {
        override fun onItemClick(videoModel: VideoModel) {
            showMoreOptionBottomSheet(videoModel)
        }

        override fun onSelectionStateChanged(selectedItems: Set<VideoModel>) {
            val selectedCount = selectedItems.size
            if (selectedCount==0){
                binding?.tvAppTitle?.visibility = View.VISIBLE
                binding?.llSelectItem?.visibility = View.GONE
            }else{
                binding?.tvAppTitle?.visibility = View.GONE
                binding?.llSelectItem?.visibility = View.VISIBLE
                binding?.tvSelectedCount?.text = "$selectedCount Selected"
                binding?.ivDeleteSelected?.setOnClickListener {
                    showDeleteSelectedVideosConfirmationDialog(selectedItems)
                    binding?.tvAppTitle?.visibility = View.VISIBLE
                    binding?.llSelectItem?.visibility = View.GONE
                }
            }
        }


    }

    private fun showMoreOptionBottomSheet(video: VideoModel) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val dialogBinding = BottomSheetMoreOptionBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(dialogBinding.root)
        val videoFile = File(video.filePath)
        dialogBinding.folderName.text = video.fileName
        dialogBinding.folderDateMenu.text = VideoUtils.getCreationDate(video.lastModified)
        Glide.with(dialogBinding.root)
            .load(videoFile)
            .centerCrop()
            .override(200, 200)
            .placeholder(R.drawable.recordings_filled)
            .into(dialogBinding.folderIcon)

        dialogBinding.itemPlay.setOnClickListener {
            playMediaFile(videoFile)
        }

        dialogBinding.itemDownload.setOnClickListener {
            bottomSheetDialog.dismiss()
            val folderPath = PreferenceUtil.savedFolderUri
            context?.let { context ->
                if (PermissionUtils.isFolderAccessGranted(context, folderPath) && folderPath != null) {
                    saveVideo(folderPath, videoFile)
                } else {
                    folderAccessLauncher.launch(null)
                }
            }
        }

        dialogBinding.itemShare.setOnClickListener {
            shareVideo(videoFile)
            bottomSheetDialog.dismiss()
        }

        dialogBinding.itemRename.setOnClickListener {
            renameVideoDialog(videoFile)
            bottomSheetDialog.dismiss()
        }

        dialogBinding.itemDelete.setOnClickListener {
            showDeleteVideoConfirmationDialog(videoFile)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }


    private fun saveVideo(folderPath: String, video: File){
        lifecycleScope.launch {
            val progressDialog = showProgressDialog()
            var success = false
            val uri = folderPath.toUri()
            uri.let {
                success = saveVideoToFolder(requireContext(), video, it)
            }
            progressDialog.dismiss()
            val message = if (success) {
                "Video saved to ${VideoUtils.getHumanReadablePath(requireContext(), uri)}"
            } else {
                "Failed to save video to Downloads"
            }
            context?.showToast(message)
        }
    }

    private suspend fun saveVideoToFolder(context: Context,sourceFile:File, folderUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            val displayName = "${sourceFile.name}.mp4"
            var success = false

            try {
                // Convert the Tree URI to a Document URI
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))

                // Create a new document within the provided folder URI
                val resolver = context.contentResolver
                val videoUri = DocumentsContract.createDocument(resolver, documentUri, "video/mp4", displayName)

                if (videoUri != null) {
                    resolver.openOutputStream(videoUri)?.use { outputStream ->
                        copyFile(sourceFile, outputStream)
                        success = true
                    }
                } else {
                    Log.e("SaveVideo", "Failed to create document in the specified folder.")
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("SaveVideo", "Error saving video", e)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Log.e("SaveVideo", "Invalid URI: ${folderUri}", e)
            }

            success
        }
    }



    private fun copyFile(sourceFile: File, outputStream: OutputStream) {
        FileInputStream(sourceFile).use { inputStream ->
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
        }
    }

    private fun shareVideo(videoFile:File) {
        try {
            val videoUri = FileProvider.getUriForFile(
                requireContext(),
                getString(R.string.file_provider_authority),
                videoFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TITLE, videoFile.name) // Set the title of the shared file
            }

            val chooser = Intent.createChooser(shareIntent, "Share video via")
            if (shareIntent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(chooser)
            } else {
                context?.showToast("No app available to share video")
            }
        } catch (e: IllegalArgumentException) {
            Log.e("RecordingsScreenFragment", "Failed to share video: ${videoFile.name}", e)
            context?.showToast("Try Downloading First Then Share")
        }
    }

    private fun playMediaFile(mediaFile: File) {
        try {
            val fileUri = FileProvider.getUriForFile(
                requireContext(),
                getString(R.string.file_provider_authority),
                mediaFile
            )

            // Determine the MIME type based on the file extension
            val mimeType = when (mediaFile.extension) {
                "mp4" -> "video/mp4"
                "aac" -> "audio/aac"
                else -> null
            }

            if (mimeType == null) {
                Log.e("RecordingsScreenFragment", "Unsupported file format: ${mediaFile.extension}")
                context?.showToast("Unsupported file format")
                return
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Log.d("RecordingsScreenFragment", "Playing media file: ${mediaFile.name}")

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
            } else {
                Log.e("RecordingsScreenFragment", "No app available to handle media playback.")
                context?.showToast("No app available. Try downloading a suitable app to play this file.")
            }

        } catch (e: IllegalArgumentException) {
            Log.e("RecordingsScreenFragment", "Failed to play media file: ${mediaFile.name}", e)
            context?.showToast("Failed to play the file. Please try downloading it first.")
        }
    }


    private fun renameVideoDialog(video: File) {
        val dialogView = requireActivity().layoutInflater.inflate(R.layout.dialog_rename, null)
        val editTextNewName = dialogView.findViewById<EditText>(R.id.et_rename)
        editTextNewName.setText(video.nameWithoutExtension)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename")
            .setView(dialogView)
            .setPositiveButton("Rename") { dialog, _ ->
                val newName = editTextNewName.text.toString()
                if (newName.isNotEmpty()) {
                    viewModel.renameVideo(video, newName)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun showDeleteSelectedVideosConfirmationDialog(selectedVideos: Set<VideoModel>) {
        val numberOfVideos = selectedVideos.size
        val message = "Are you sure you want to delete $numberOfVideos video${if (numberOfVideos > 1) "s" else ""} from the app?"
        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        val progressDialog = MaterialAlertDialogBuilder(requireActivity())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        // Set the initial progress message
        dialogBinding.progressText.text = "Deleting videos..."

        // Show confirmation dialog
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Delete Selected Videos")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                progressDialog.show()
                viewModel.deleteSelectedVideos(selectedVideos, progressDialog) { updatedMessage ->
                    // Update the message when needed
                    dialogBinding.progressText.text = updatedMessage
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }


    private fun showDeleteVideoConfirmationDialog(video: File) {
        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Delete Video")
            .setMessage("Are you sure you want to delete this video?")
            .setPositiveButton("Delete") { dialog, which ->
                viewModel.deleteVideo(video)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun showSaveInfoDialog() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Video Saved to App Storage!")
            .setMessage("Your videos are safely stored in the app's storage. 📦 This means no distortion or errors, even for longer recordings! 👍\n\nWant to save it elsewhere? You can always do it manually. 😉\n\nEnjoy smooth, uninterrupted recordings with zero hassle! 🚀")
            .setPositiveButton("Got it!") { dialog, _ ->
                PreferenceUtil.showScopedStorageDialog = false
                dialog.dismiss()
            }
            .create()
            .show()
    }


    private fun showProgressDialog(): androidx.appcompat.app.AlertDialog {
        val progressDialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        return MaterialAlertDialogBuilder(requireContext())
            .setView(progressDialogView)
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        viewModel.removeObserver()
    }
}
