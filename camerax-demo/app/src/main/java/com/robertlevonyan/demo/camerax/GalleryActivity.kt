package com.robertlevonyan.demo.camerax

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class GalleryActivity : AppCompatActivity() {
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var imageUris: List<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)


        val recyclerView = findViewById<RecyclerView>(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true) // to improve performance
        recyclerView.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.image_spacing), 3))

        val imageUris = getAllImageUris(this) as ArrayList<Uri>
        imageUris.reverse() //From newest to oldest. If I don't do this, it's from old to new
        imageAdapter = ImageAdapter(imageUris, this) { position ->
            openImageDetailActivity(imageUris, position)
        }
        recyclerView.adapter = imageAdapter
    }

    private fun getAllImageUris(context: Context): List<Uri> {
        val imageUris = mutableListOf<Uri>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(MediaStore.Images.Media._ID)

            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf("DCIM/CameraXDemo/"),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    imageUris.add(uri)
                }
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), "CameraXDemo")
            if (directory.exists()) {
                val files = directory.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".png") }
                files?.forEach { file ->
                    val uri = Uri.fromFile(file)
                    imageUris.add(uri)
                }
            }
        }
        Log.d("GalleryActivity", "Loaded URIs: $imageUris")
        return imageUris
    }

    private fun openImageDetailActivity(imageUris: ArrayList<Uri>, currentPosition: Int) {
        val intent = Intent(this, ImageDetailActivity::class.java).apply {
            putParcelableArrayListExtra("IMAGE_URIS", imageUris)
            putExtra("CURRENT_POSITION", currentPosition)
        }
        startActivity(intent)
    }

    class ImageAdapter(
        private val images: ArrayList<Uri>,
        private val context: Context,
        private val onImageClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
        private val space = context.resources.getDimensionPixelSize(R.dimen.image_spacing) // Assuming you have defined this in dimens.xml
        private val imageWidth = (context.resources.displayMetrics.widthPixels / 3) - (2 * space)

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = images[position]
            Glide.with(context).load(uri).into(holder.imageView)
            holder.imageView.setOnClickListener {
                onImageClick(position)
            }
        }

        override fun getItemCount(): Int {
            return images.size
        }
        fun updateData(newImages: List<Uri>) {
            images.clear()
            images.addAll(newImages)
            notifyDataSetChanged()
        }

    }

    class SpacesItemDecoration(private val space: Int, private val spanCount: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view) // item position
            val column = position % spanCount // item column

            with(outRect) {
                left = space - column * space / spanCount
                right = (column + 1) * space / spanCount

                if (position < spanCount) {
                    top = space
                }
                bottom = space
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Fetch new image URIs
        val newImageUris = getAllImageUris(this) as ArrayList<Uri>
        // Ensure the list is in the correct order (newest first)
        newImageUris.reverse()
        // Update the adapter with the new list
        imageAdapter.updateData(newImageUris)
    }

}



