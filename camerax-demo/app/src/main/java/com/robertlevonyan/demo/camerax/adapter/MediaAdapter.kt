package com.robertlevonyan.demo.camerax.adapter

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.robertlevonyan.demo.camerax.R
import com.robertlevonyan.demo.camerax.utils.layoutInflater

/**
 * This is an adapter to preview taken photos or videos
 * */
class MediaAdapter(
    private val onItemClick: (Boolean, Uri) -> Unit,
    private val onDeleteClick: (Boolean, Uri) -> Unit,
) : ListAdapter<Media, MediaAdapter.PicturesViewHolder>(MediaDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PicturesViewHolder(parent.context.layoutInflater.inflate(R.layout.item_picture, parent, false))

    override fun onBindViewHolder(holder: PicturesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PicturesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePreview: ImageView = itemView.findViewById(R.id.imagePreview)

        fun bind(item: Media) {
            imagePreview.load(item.uri)
            imagePreview.setOnClickListener { onItemClick(item.isVideo, item.uri) }
        }
    }
}
