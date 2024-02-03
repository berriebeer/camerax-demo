package com.robertlevonyan.demo.camerax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class HomePage : AppCompatActivity() {

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // Initialize the launcher
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val intentOpenCameraWithImage = Intent(this, MainActivity::class.java)
                intentOpenCameraWithImage.putExtra("SelectedImageUri", it.toString())
                startActivity(intentOpenCameraWithImage)
            }
        }

        val cardViewGallery: CardView = findViewById(R.id.cardViewRecreatePicture)
        cardViewGallery.setOnClickListener {
            // Use the launcher to start the activity for result
            imagePickerLauncher.launch("image/*")
        }

        // Find the button and set the click listener
        val cardViewOpenPictures: CardView = findViewById(R.id.cardViewOpenPictures)
        cardViewOpenPictures.setOnClickListener {
            openPictures()
        }

    }
        private fun openPictures() {
            val intent = Intent(this, GalleryActivity::class.java)
            startActivity(intent)
        }
    }