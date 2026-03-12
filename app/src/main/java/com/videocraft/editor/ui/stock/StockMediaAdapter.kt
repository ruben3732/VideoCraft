package com.videocraft.editor.ui.stock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.videocraft.editor.R
import com.videocraft.editor.data.model.StockMediaItem
import com.videocraft.editor.data.model.StockType

class StockMediaAdapter(
    private val onItemClick: (StockMediaItem) -> Unit
) : ListAdapter<StockMediaItem, StockMediaAdapter.MediaViewHolder>(MediaDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stock_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.iv_media_thumbnail)
        private val typeIndicator: View = view.findViewById(R.id.v_type_indicator)
        private val tvAttribution: TextView = view.findViewById(R.id.tv_attribution)
        private val durationBadge: TextView = view.findViewById(R.id.tv_duration_badge)

        fun bind(item: StockMediaItem) {
            // Type indicator color
            typeIndicator.setBackgroundColor(
                when (item.type) {
                    StockType.PHOTO -> 0xFF6200EE.toInt()
                    StockType.VIDEO -> 0xFF03DAC5.toInt()
                    StockType.GIF -> 0xFFFF6D00.toInt()
                }
            )

            // Duration badge for videos
            if (item.type == StockType.VIDEO && item.duration > 0) {
                durationBadge.text = "${item.duration}s"
                durationBadge.visibility = View.VISIBLE
            } else {
                durationBadge.visibility = View.GONE
            }

            tvAttribution.text = item.attribution.take(30)

            // Load thumbnail with Glide
            Glide.with(itemView.context)
                .load(item.thumbnailUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.bg_media_placeholder)
                .into(thumbnail)

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    class MediaDiff : DiffUtil.ItemCallback<StockMediaItem>() {
        override fun areItemsTheSame(old: StockMediaItem, new: StockMediaItem) = old.id == new.id
        override fun areContentsTheSame(old: StockMediaItem, new: StockMediaItem) = old == new
    }
}
