package com.robertlevonyan.demo.camerax

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

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

        val buttonCamera: Button = findViewById(R.id.btnOpenCamera)
        buttonCamera.setOnClickListener {
            val intentCamera = Intent(this, MainActivity::class.java)
            startActivity(intentCamera)
        }

        val buttonGallery: Button = findViewById(R.id.btnOpenGallery)
        buttonGallery.setOnClickListener {
            // Use the launcher to start the activity for result
            imagePickerLauncher.launch("image/*")
        }
    }
}
