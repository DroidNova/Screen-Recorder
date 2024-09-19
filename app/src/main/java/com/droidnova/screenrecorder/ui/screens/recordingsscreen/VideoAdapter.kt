package com.droidnova.screenrecorder.ui.screens.recordingsscreen

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.databinding.ItemVideoDetailsBinding
import com.droidnova.screenrecorder.utils.VideoUtils

class VideoAdapter(private val listener: VideoItemClickListener) :
    ListAdapter<VideoModel, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private val selectedItems = mutableSetOf<VideoModel>()
    private var selectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoDetailsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        holder.bind(video, isSelected(video))
    }

    inner class VideoViewHolder(private val binding: ItemVideoDetailsBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            setupClickListeners()
        }

        private fun setupClickListeners() {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val video = getItem(position)
                    if (selectionMode) {
                        toggleSelection(video)
                    } else {
                        listener.onItemClick(video)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val video = getItem(position)
                    if (!selectionMode) {
                        selectionMode = true
                    }
                    toggleSelection(video)
                }
                true
            }
        }

        fun bind(videoModel: VideoModel, isSelected: Boolean) {
            val context = binding.root.context
            binding.apply {
                tvVideoName.text = videoModel.fileName
                tvVideoSize.text = videoModel.fileSize
                tvVideoCreationDate.text = VideoUtils.getCreationDate(videoModel.lastModified)
                tvVideoLength.text = videoModel.videoDuration

                Glide.with(binding.root)
                    .load(videoModel.filePath)
                    .centerCrop()
                    .override(200, 200)
                    .placeholder(R.drawable.recordings_filled)
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivVideoThumbnail)

                // Change background color based on selection state
                if (isSelected){
                    Log.e("myTag","isSelected")
                    binding.clCardView.setBackgroundColor(context.getColor(R.color.cardSelectedBackgroundColor))
                    binding.tvVideoName.setTextColor(context.getColor(R.color.selectedPrimaryTextColor))
                    binding.tvVideoSize.setTextColor(context.getColor(R.color.selectedSecondaryTextColor))
                    binding.tvVideoCreationDate.setTextColor(context.getColor(R.color.selectedSecondaryTextColor))

                }else{
                    binding.clCardView.setBackgroundColor(context.getColor(R.color.cardBackgroundColor))
                    binding.tvVideoName.setTextColor(context.getColor(R.color.textColorPrimary))
                    binding.tvVideoSize.setTextColor(context.getColor(R.color.textColorSecondary))
                    binding.tvVideoCreationDate.setTextColor(context.getColor(R.color.textColorSecondary))
                }
            }
        }
    }

    private fun toggleSelection(videoModel: VideoModel) {
        if (selectedItems.contains(videoModel)) {
            selectedItems.remove(videoModel)
        } else {
            selectedItems.add(videoModel)
        }
        notifyItemChanged(currentList.indexOf(videoModel))
        updateSelectionState()
    }

    private fun isSelected(videoModel: VideoModel): Boolean {
        return selectedItems.contains(videoModel)
    }

    private fun updateSelectionState() {
        if (selectedItems.isEmpty()) {
            selectionMode = false
        }
        listener.onSelectionStateChanged(selectedItems)
    }

    fun clearSelection() {
        selectedItems.clear()
        selectionMode = false
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoModel>() {
        override fun areItemsTheSame(oldItem: VideoModel, newItem: VideoModel): Boolean {
            return oldItem.filePath == newItem.filePath
        }

        override fun areContentsTheSame(oldItem: VideoModel, newItem: VideoModel): Boolean {
            return oldItem == newItem
        }
    }

    interface VideoItemClickListener {
        fun onItemClick(videoModel: VideoModel)
        fun onSelectionStateChanged(selectedItems: Set<VideoModel>)
    }
}
