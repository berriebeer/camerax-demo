package com.robertlevonyan.demo.camerax

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.widget.ImageView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageUriString = intent.getStringExtra("SelectedImageUri")
        if (imageUriString != null ){
            val imageUri = Uri.parse(imageUriString)
            val overlayImageView: ImageView = findViewById(R.id.imageOverlay)
        overlayImageView.setImageURI(imageUri)
        }
    }
}
