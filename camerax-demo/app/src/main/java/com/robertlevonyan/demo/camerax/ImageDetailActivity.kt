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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Receive the list of image URIs and the current position from the intent
        val imageUris: ArrayList<Uri> = intent.getParcelableArrayListExtra("IMAGE_URIS") ?: arrayListOf()
        val currentPosition = intent.getIntExtra("CURRENT_POSITION", 0)
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
                    // Handle share navigation
                    true
                }
                else -> false
            }
        }

    }
}