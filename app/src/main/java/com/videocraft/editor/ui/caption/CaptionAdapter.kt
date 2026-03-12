package com.videocraft.editor.ui.caption

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.videocraft.editor.R
import com.videocraft.editor.data.model.Caption
import com.videocraft.editor.utils.toTimeString

class CaptionAdapter(
    private val onEdit: (Caption) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onCaptionClick: (Caption) -> Unit
) : ListAdapter<Caption, CaptionAdapter.CaptionViewHolder>(CaptionDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_caption, parent, false)
        return CaptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CaptionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CaptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tv_caption_text)
        private val tvTime: TextView = view.findViewById(R.id.tv_caption_time)
        private val tvLanguage: TextView = view.findViewById(R.id.tv_caption_language)
        private val btnEdit: MaterialButton = view.findViewById(R.id.btn_edit_caption)
        private val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete_caption)

        fun bind(caption: Caption) {
            tvText.text = caption.text.ifBlank { "(empty)" }
            tvTime.text = "${caption.startTimeMs.toTimeString()} → ${caption.endTimeMs.toTimeString()}"
            tvLanguage.text = caption.language.name

            // Visual indicator for edited captions
            if (caption.isEdited) {
                tvText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_edit_indicator, 0)
            }

            btnEdit.setOnClickListener { onEdit(caption) }
            btnDelete.setOnClickListener { onDelete(caption.id) }
            itemView.setOnClickListener { onCaptionClick(caption) }
        }
    }

    class CaptionDiff : DiffUtil.ItemCallback<Caption>() {
        override fun areItemsTheSame(old: Caption, new: Caption) = old.id == new.id
        override fun areContentsTheSame(old: Caption, new: Caption) = old == new
    }
}
