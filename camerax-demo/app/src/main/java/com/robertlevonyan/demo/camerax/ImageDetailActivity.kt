package com.robertlevonyan.demo.camerax

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView

class ImageDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detail)

        val imageUri = Uri.parse(intent.getStringExtra("IMAGE_URI"))
        val touchImageView = findViewById<ImageView>(R.id.fullImageView)
        Glide.with(this).load(imageUri).into(touchImageView)

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