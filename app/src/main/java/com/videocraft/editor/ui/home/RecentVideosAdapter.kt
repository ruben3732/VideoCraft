package com.videocraft.editor.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.videocraft.editor.R
import com.videocraft.editor.utils.getVideoDurationMs
import com.videocraft.editor.utils.toSimpleTimeString

class RecentVideosAdapter(
    private val onVideoClick: (Uri) -> Unit
) : ListAdapter<Uri, RecentVideosAdapter.VideoViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
        private val duration: TextView = view.findViewById(R.id.tv_duration)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .placeholder(R.drawable.bg_video_placeholder)
                .into(thumbnail)

            val durationMs = uri.getVideoDurationMs(itemView.context)
            duration.text = durationMs.toSimpleTimeString()

            itemView.setOnClickListener { onVideoClick(uri) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
    }
}
