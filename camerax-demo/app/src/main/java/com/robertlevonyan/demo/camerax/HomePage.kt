package com.robertlevonyan.demo.camerax

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.content.Intent
import android.net.Uri


class HomePage : AppCompatActivity() {

    companion object {
        const val PICK_PHOTO_REQUEST_CODE = 100 // This can be any integer you choose.
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        val buttonCamera: Button = findViewById(R.id.btnOpenCamera)
        buttonCamera.setOnClickListener {
            val intentCamera = Intent(this, MainActivity::class.java)
            startActivity(intentCamera)
        }

        val buttonGallery: Button = findViewById(R.id.btnOpenGallery)
        buttonGallery.setOnClickListener {
            val intentGallery = Intent(Intent.ACTION_PICK)
            intentGallery.type = "image/*"
            startActivityForResult(intentGallery, PICK_PHOTO_REQUEST_CODE)

        }
}
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PHOTO_REQUEST_CODE && resultCode == RESULT_OK) {
            val selectedImageUri: Uri? = data?.data
            val intentOpenCameraWithImage = Intent(this, MainActivity::class.java)
            intentOpenCameraWithImage.putExtra("SelectedImageUri", selectedImageUri.toString())
            startActivity(intentOpenCameraWithImage)
        }
    }
}