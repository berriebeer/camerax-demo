package com.robertlevonyan.demo.camerax

import android.content.ContentUris
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val recyclerView = findViewById<RecyclerView>(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true) // to improve performance
        recyclerView.addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.image_spacing), 3))

        val imageUris = getAllImageUris(this)
        imageAdapter = ImageAdapter(imageUris, this)
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
        return imageUris
    }

    class ImageAdapter(private val images: List<Uri>, private val context: Context) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
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
            Glide.with(context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView)
        }

        override fun getItemCount(): Int {
            return images.size
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


}



