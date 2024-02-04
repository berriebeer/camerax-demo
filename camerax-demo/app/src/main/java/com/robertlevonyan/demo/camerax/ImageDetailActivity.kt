package com.robertlevonyan.demo.camerax

import ImageSliderAdapter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.robertlevonyan.demo.camerax.databinding.ActivityImageDetailBinding

class ImageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageDetailBinding
    private lateinit var imageUris: ArrayList<Uri>
    private var currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUris = intent.getParcelableArrayListExtra("IMAGE_URIS") ?: arrayListOf()
        currentPosition = intent.getIntExtra("CURRENT_POSITION", 0)

        // Receive the list of image URIs and the current position from the intent
        //val imageUris: ArrayList<Uri> = intent.getParcelableArrayListExtra("IMAGE_URIS") ?: arrayListOf()
        //val currentPosition = intent.getIntExtra("CURRENT_POSITION", 0)
        Log.d("ImageDetailActivity", "Received image URIs: $imageUris")
        Log.d("ImageDetailActivity", "Current position: $currentPosition")

        val adapter = ImageSliderAdapter(imageUris) { isZoomed ->
            binding.viewPager2.isUserInputEnabled = !isZoomed
        }

        // Set up the ViewPager with the adapter and the current item
        binding.viewPager2.adapter = adapter
        binding.viewPager2.setCurrentItem(currentPosition, false)


        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.slide -> {
                    // Handle slide navigation
                    true
                }
                R.id.edit -> {
                    // Handle animate navigation
                    true
                }
                R.id.share -> {
                    // Handle show navigation
                    true
                }
                R.id.delete -> {
                    deleteCurrentImage()
                    true
                }
                else -> false
            }
        }

    }
    private fun deleteCurrentImage() {
        val currentUri = imageUris.getOrNull(currentPosition) ?: return
        // Optionally, delete the image file from storage
        deleteImageFromStorage(currentUri)

        // Remove the URI from the list and notify the adapter
        imageUris.removeAt(currentPosition)
        (binding.viewPager2.adapter as? ImageSliderAdapter)?.notifyDataSetChanged()

        // Optionally, close the activity if there are no more images
        if (imageUris.isEmpty()) {
            finish()
        } else {
            // Adjust the current position if needed
            currentPosition = maxOf(0, currentPosition - 1)
            binding.viewPager2.setCurrentItem(currentPosition, true)
        }
    }

    private fun deleteImageFromStorage(uri: Uri) {
        // Implementation depends on how images are stored. Example for MediaStore:
        try {
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("ImageDetailActivity", "Error deleting image", e)
        }
    }
}