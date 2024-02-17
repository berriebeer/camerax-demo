package com.robertlevonyan.demo.camerax

import ImageSliderAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.robertlevonyan.demo.camerax.databinding.ActivityImageDetailBinding
import com.yalantis.ucrop.UCrop
import java.io.File

class ImageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageDetailBinding
    private lateinit var imageUris: ArrayList<Uri>
    private var currentPosition: Int = 0

    private lateinit var cropActivityResultLauncher: ActivityResultLauncher<Intent>


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


        cropActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let {
                    // Here you can update your UI with the cropped image URI
                    Log.d("Cropped Image URI", it.toString())
                    // Update your imageView or list with the new cropped image URI
                    // For example: imageView.setImageURI(resultUri)
                }
            }
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.slide -> {
                    // Handle slide navigation
                    true
                }
                R.id.edit -> {
                    val sourceUri = imageUris[currentPosition] // Assuming you have a list of URIs and a currentPosition
                    val destinationUri = Uri.fromFile(File(cacheDir, "Cropped_${System.currentTimeMillis()}.jpg"))

                    val options = UCrop.Options().apply {
                        // Customize uCrop options as needed
                    }

                    val uCrop = UCrop.of(sourceUri, destinationUri).withOptions(options)
                    cropActivityResultLauncher.launch(uCrop.getIntent(this@ImageDetailActivity))
                    true
                }
                R.id.share -> {
                    shareCurrentImage()
                    true
                }
                R.id.delete -> {
                    confirmAndDeleteCurrentImage()
                    true
                }
                else -> false
            }
        }

    }
    private fun shareCurrentImage() {
        val currentUri = imageUris.getOrNull(currentPosition) ?: return
        val mimeType = contentResolver.getType(currentUri) ?: "image/*" // Fallback to a generic type if needed
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, currentUri)
            type = mimeType
            // Optional: You can also add a subject or text to share along with the image
            putExtra(Intent.EXTRA_TEXT, "Look at this photo I made with ThenAndNow Camera!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant temporary read permission
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image"))
    }

    private fun deleteImageFromStorage(uri: Uri) {
        // Implementation depends on how images are stored. Example for MediaStore:
        try {
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("ImageDetailActivity", "Error deleting image", e)
        }
    }
    private fun confirmAndDeleteCurrentImage() {
        val currentUri = imageUris.getOrNull(currentPosition) ?: return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete)) // Replace with your string resource for the title
            .setMessage(getString(R.string.are_you_sure_delete)) // Replace with your string resource for the message
            .setPositiveButton(getString(R.string.delete)) { dialog, which ->
                // Proceed with the deletion
                deleteImageFromStorage(currentUri)

                // Remove the URI from the list and notify the adapter
                imageUris.removeAt(currentPosition)
                (binding.viewPager2.adapter as? ImageSliderAdapter)?.notifyDataSetChanged()

                // Close the activity if there are no more images, or adjust the current position
                if (imageUris.isEmpty()) {
                    finish()
                } else {
                    currentPosition = maxOf(0, currentPosition - 1)
                    binding.viewPager2.setCurrentItem(currentPosition, true)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}

