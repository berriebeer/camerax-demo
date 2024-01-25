package com.robertlevonyan.demo.camerax.fragments

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import coil.load
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.slider.Slider
import com.ortiz.touchview.TouchImageView
import com.robertlevonyan.demo.camerax.R
import com.robertlevonyan.demo.camerax.analyzer.LuminosityAnalyzer
import com.robertlevonyan.demo.camerax.databinding.FragmentCameraBinding
import com.robertlevonyan.demo.camerax.enums.CameraTimer
import com.robertlevonyan.demo.camerax.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

class CameraFragment : BaseFragment<FragmentCameraBinding>(R.layout.fragment_camera) {

    // An instance for display manager to get display change callbacks
    private val displayManager by lazy { requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }

    // An instance of a helper function to work with Shared Preferences
    private val prefs by lazy { SharedPrefsManager.newInstance(requireContext()) }

    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // A lazy instance of the current fragment's view binding
    override val binding: FragmentCameraBinding by lazy { FragmentCameraBinding.inflate(layoutInflater) }

    private var displayId = -1

    // Selector showing which camera is selected (front or back)
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var hdrCameraSelector: CameraSelector? = null

    // Selector showing which flash mode is selected (on, off or auto)
    private var flashMode by Delegates.observable(FLASH_MODE_OFF) { _, _, new ->
        binding.btnFlash.setImageResource(
            when (new) {
                FLASH_MODE_ON -> R.drawable.ic_flash_on
                FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                else -> R.drawable.ic_flash_off
            }
        )
    }

    // Selector showing is grid enabled or not
    private var hasGrid = false

    // Selector showing is hdr enabled or not (will work, only if device's camera supports hdr on hardware level)
    private var hasHdr = false

    // Selector showing is there any selected timer and it's value (3s or 10s)
    private var selectedTimer = CameraTimer.OFF

    // Remembers what the current rotation of the ImageView viewImageOverlay, to be manipulated with the btnRotate
    private var currentRotation = 0f

    // Variable to hold the current value of the Slider
    private var sliderCurrentValue: Float = 0.5f // Default value or saved state

    // Declare CameraControl and CameraInfo as class variables in CameraFragment, used for zoom:
    private lateinit var cameraControl: CameraControl
    private lateinit var cameraInfo: CameraInfo

    // Add a property to keep track of the lock state. Used for initializeLockButton/setTouchImageViewInteraction
    private var isLocked = false


    /**
     * A display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                preview?.targetRotation = view.display.rotation
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    private fun initializeTouchImageView(imageUri: String) {
        // Convert the String URI to a Uri object
        val imageUriParsed = Uri.parse(imageUri)
        // Get the image stream and decode it into a Bitmap
        val imageStream = activity?.contentResolver?.openInputStream(imageUriParsed)
        val selectedImage = BitmapFactory.decodeStream(imageStream)
        // Find the imageView from the view hierarchy
        val touchImageView = view?.findViewById<TouchImageView>(R.id.viewImageOverlay)?.apply {
            // Reset the properties to default
            setZoom(1.0f)
            // Reset position to center the image
            scaleX = 1f // Reset mirror
            rotation = 0f // Reset rotation
            alpha = 0.5f // Reset alpha to fully opaque

            // Set the Bitmap to the TouchImageView
            setImageBitmap(selectedImage)
            minZoom = 0.1f
        }

        /**
         * Slider
         */
        // Find the slider view and set up a listener to change the image alpha
        val slider = view?.findViewById<Slider>(R.id.sliderAlpha)
        // Set a listener on the slider to change the ImageView's alpha value
        slider?.addOnChangeListener { _, value, _ ->
            touchImageView?.alpha = value
            sliderCurrentValue = value //save the current value
            Log.d("CameraFragment", "Current sliderCurrentValue: ${touchImageView?.alpha}")
        }

        /**
         * Select Image
         */
        val btnSelectImage: ImageButton = binding.btnSelectImage // Assuming you have this button in your layout
        btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        /**
         * Hide ImageOverlay (and restore alpha once toggled back on)
         */
        // Find the button for ToggleOverlay and do it
        val btnToggleOverlay: ImageButton? = view?.findViewById(R.id.btnToggleOverlay)
        btnToggleOverlay?.setOnClickListener {
            touchImageView?.let { imageView ->
                if (imageView.visibility == View.VISIBLE) {
                    // Save the current alpha value before making invisible
                    sliderCurrentValue = imageView.alpha
                    Log.d("CameraFragment", "Alpha before made invisible: $sliderCurrentValue")
                    imageView.visibility = View.GONE
                } else {
                    // Restore the alpha value after making visible
                    Log.d("CameraFragment", "Alpha before making visible again: ${imageView.alpha}")
                    imageView.visibility = View.VISIBLE
                    // Post the action to the imageView's handler to avoid potential threading issues
                    imageView.postDelayed({
                        // Check again if imageView is visible to avoid potential issues if the view was hidden again.
                        if(imageView.visibility == View.VISIBLE) {
                            imageView.alpha = sliderCurrentValue
                            Log.d("CameraFragment", "Alpha after making visible again2: ${imageView.alpha}")
                        }
                    }, 350)
                }
            }
        }

        /**
         * Mirror
         */
        // Find the button for Mirroring the viewImageOverlay and do it
        val btnMirrorImage: ImageButton? = view?.findViewById(R.id.btnMirrorImage)
        btnMirrorImage?.setOnClickListener {
            // Logic to mirror the image
            touchImageView?.scaleX = (touchImageView?.scaleX ?: 1f) * -1
        }

        /**
         * Rotate 90degree ccw
         */
        // Find the button for Rotating the viewImageOverlay and do it
        val btnRotate: ImageButton? = view?.findViewById(R.id.btnRotate)
        btnRotate?.setOnClickListener {
            // Increase the current rotation by 90 degrees (counter clockwise)
            currentRotation -= 90
            // Apply the rotation to the ImageView
            touchImageView?.rotation = currentRotation
        }

    }

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        flashMode = prefs.getInt(KEY_FLASH, FLASH_MODE_OFF)
        hasGrid = prefs.getBoolean(KEY_GRID, false)
        hasHdr = prefs.getBoolean(KEY_HDR, false)

        // Check if an image URI is passed and initialize the TouchImageView if it is
        val imageUriString = activity?.intent?.getStringExtra("SelectedImageUri")
        if (!imageUriString.isNullOrEmpty()) {
            initializeTouchImageView(imageUriString)
        } else {
            // If no image URI is passed, set the visibility of the related buttons to GONE
            view.findViewById<TouchImageView>(R.id.viewImageOverlay).visibility = View.GONE
            view.findViewById<ImageButton>(R.id.btnSelectImage).visibility = View.GONE
            view.findViewById<ImageButton>(R.id.btnLock).visibility = View.GONE
            view.findViewById<ImageButton>(R.id.btnMirrorImage).visibility = View.GONE
            view.findViewById<ImageButton>(R.id.btnRotate).visibility = View.GONE
            view.findViewById<ImageButton>(R.id.btnToggleOverlay).visibility = View.GONE
            view.findViewById<Slider>(R.id.sliderAlpha).visibility = View.GONE
        }

        //Launcher for selecting image
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Update the TouchImageView with the selected image
                initializeTouchImageView(uri.toString())
            }
        }

        initializeLockButton()

        /** Pinch to Zoom
         */
        val viewFinder = view.findViewById<PreviewView>(R.id.viewFinder)

        super.onViewCreated(view, savedInstanceState)

        val scaleGestureDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                Log.d("CameraFragment", "Pinch to Zoom is triggered")
                // Calculate desired zoom ratio
                val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1F
                val delta = detector.scaleFactor
                cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        })

        // Attach the pinch gesture listener to the viewfinder
        viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            true
        }

        initViews()


        displayManager.registerDisplayListener(displayListener, null)

        binding.run {
            viewFinder.addOnAttachStateChangeListener(object :
                View.OnAttachStateChangeListener {
                override fun onViewDetachedFromWindow(v: View) =
                    displayManager.registerDisplayListener(displayListener, null)

                override fun onViewAttachedToWindow(v: View) =
                    displayManager.unregisterDisplayListener(displayListener)
            })

            btnTakePicture.setOnClickListener { takePicture() }
            btnGallery.setOnClickListener { openPreview() }
            btnSwitchCamera.setOnClickListener { toggleCamera() }
            btnTimer.setOnClickListener { selectTimer() }
            btnGrid.setOnClickListener { toggleGrid() }
            btnFlash.setOnClickListener { selectFlash() }
            btnHdr.setOnClickListener { toggleHdr() }
            btnTimerOff.setOnClickListener { closeTimerAndSelect(CameraTimer.OFF) }
            btnTimer3.setOnClickListener { closeTimerAndSelect(CameraTimer.S3) }
            btnTimer10.setOnClickListener { closeTimerAndSelect(CameraTimer.S10) }
            btnFlashOff.setOnClickListener { closeFlashAndSelect(FLASH_MODE_OFF) }
            btnFlashOn.setOnClickListener { closeFlashAndSelect(FLASH_MODE_ON) }
            btnFlashAuto.setOnClickListener { closeFlashAndSelect(FLASH_MODE_AUTO) }
            btnExposure.setOnClickListener { flExposure.visibility = View.VISIBLE }
            flExposure.setOnClickListener { flExposure.visibility = View.GONE }


    }
    }

    /**
     * Create some initial states
     * */
    private fun initViews() {
        binding.btnGrid.setImageResource(if (hasGrid) R.drawable.ic_grid_on else R.drawable.ic_grid_off)
        binding.groupGridLines.visibility = if (hasGrid) View.VISIBLE else View.GONE
        adjustInsets()
    }

    /**
     * This methods adds all necessary margins to some views based on window insets and screen orientation
     * */
    private fun adjustInsets() {
        activity?.window?.fitSystemWindows()
        binding.btnTakePicture.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.bottomMargin =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            } else {
                view.endMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).right
            }
        }
        binding.btnTimer.onWindowInsets { view, windowInsets ->
            view.topMargin = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        }
        binding.llTimerOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
        binding.llFlashOptions.onWindowInsets { view, windowInsets ->
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.topPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            } else {
                view.startPadding =
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left
            }
        }
    }

    /**
     * Change the facing of camera
     *  toggleButton() function is an Extension function made to animate button rotation
     * */
    @SuppressLint("RestrictedApi")
    fun toggleCamera() = binding.btnSwitchCamera.toggleButton(
        flag = lensFacing == CameraSelector.DEFAULT_BACK_CAMERA,
        rotationAngle = 180f,
        firstIcon = R.drawable.ic_outline_camera_rear,
        secondIcon = R.drawable.ic_outline_camera_front,
    ) {
        lensFacing = if (it) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        startCamera()
    }

//InitializeLockButton
    private fun initializeLockButton() {
        // Find the lock button and set up a click listener
        val btnLock: ImageButton = binding.btnLock // Assuming you have a button with ID btnLock in your layout
        btnLock.setOnClickListener {
            // Toggle the lock state
            isLocked = !isLocked

            // Update the button appearance based on the lock state
            btnLock.setImageResource(if (isLocked) R.drawable.ic_lock else R.drawable.ic_lock)

            // Enable or disable the interaction with the ImageOverlay and other buttons
            setTouchImageViewInteraction(!isLocked)
        }
    }

    private fun setTouchImageViewInteraction(enabled: Boolean) {
        // Enable or disable interactions with the ImageOverlay
        binding.viewImageOverlay.isEnabled = enabled

        // Enable or disable buttons and slider
        binding.btnMirrorImage.isEnabled = enabled
        binding.btnRotate.isEnabled = enabled
        binding.btnToggleOverlay.isEnabled = enabled
        binding.sliderAlpha.isEnabled = enabled
    }
    /**
     * Navigate to PreviewFragment
     * */
    private fun openPreview() {
        if (getMedia().isEmpty()) return
        view?.let { Navigation.findNavController(it).navigate(R.id.action_camera_to_preview) }
    }

    /**
     * Show timer selection menu by circular reveal animation.
     *  circularReveal() function is an Extension function which is adding the circular reveal
     * */
    private fun selectTimer() = binding.llTimerOptions.circularReveal(binding.btnTimer)

    /**
     * This function is called from XML view via Data Binding to select a timer
     *  possible values are OFF, S3 or S10
     *  circularClose() function is an Extension function which is adding circular close
     * */
    private fun closeTimerAndSelect(timer: CameraTimer) =
        binding.llTimerOptions.circularClose(binding.btnTimer) {
            selectedTimer = timer
            binding.btnTimer.setImageResource(
                when (timer) {
                    CameraTimer.S3 -> R.drawable.ic_timer_3
                    CameraTimer.S10 -> R.drawable.ic_timer_10
                    CameraTimer.OFF -> R.drawable.ic_timer_off
                }
            )
        }

    /**
     * Show flashlight selection menu by circular reveal animation.
     *  circularReveal() function is an Extension function which is adding the circular reveal
     * */
    private fun selectFlash() = binding.llFlashOptions.circularReveal(binding.btnFlash)

    /**
     * This function is called from XML view via Data Binding to select a FlashMode
     *  possible values are ON, OFF or AUTO
     *  circularClose() function is an Extension function which is adding circular close
     * */
    private fun closeFlashAndSelect(@FlashMode flash: Int) =
        binding.llFlashOptions.circularClose(binding.btnFlash) {
            flashMode = flash
            binding.btnFlash.setImageResource(
                when (flash) {
                    FLASH_MODE_ON -> R.drawable.ic_flash_on
                    FLASH_MODE_OFF -> R.drawable.ic_flash_off
                    else -> R.drawable.ic_flash_auto
                }
            )
            imageCapture?.flashMode = flashMode
            prefs.putInt(KEY_FLASH, flashMode)
        }

    /**
     * Turns on or off the grid on the screen
     * */
    private fun toggleGrid() {
        binding.btnGrid.toggleButton(
            flag = hasGrid,
            rotationAngle = 180f,
            firstIcon = R.drawable.ic_grid_off,
            secondIcon = R.drawable.ic_grid_on,
        ) { flag ->
            hasGrid = flag
            prefs.putBoolean(KEY_GRID, flag)
            binding.groupGridLines.visibility = if (flag) View.VISIBLE else View.GONE
        }
    }

    /**
     * Turns on or off the HDR if available
     * */
    private fun toggleHdr() {
        binding.btnHdr.toggleButton(
            flag = hasHdr,
            rotationAngle = 360f,
            firstIcon = R.drawable.ic_hdr_off,
            secondIcon = R.drawable.ic_hdr_on,
        ) { flag ->
            hasHdr = flag
            prefs.putBoolean(KEY_HDR, flag)
            startCamera()
        }
    }

    override fun onPermissionGranted() {
        // Each time apps is coming to foreground the need permission check is being processed
        binding.viewFinder.let { vf ->
            vf.post {
                // Setting current display ID
                displayId = vf.display.displayId
                startCamera()
                lifecycleScope.launch(Dispatchers.IO) {
                    // Do on IO Dispatcher
                    setLastPictureThumbnail()
                }
            }
        }
    }

    private fun setLastPictureThumbnail() = binding.btnGallery.post {
        getMedia().firstOrNull() // check if there are any photos or videos in the app directory
            ?.let { setGalleryThumbnail(it.uri) } // preview the last one
            ?: binding.btnGallery.setImageResource(R.drawable.ic_no_picture) // or the default placeholder
    }

    /**
     *startCamera()
     * */

    private fun startCamera() {
        // This is the CameraX PreviewView where the camera will be rendered
        val viewFinder = binding.viewFinder

        // The display information
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        // The ratio for the output image and preview
        val aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        // The display rotation
        val rotation = viewFinder.display.rotation

        // Retrieve a CameraProvider for the lifecycle of the application
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                // Camera provider is used to bind the lifecycle of cameras to the lifecycle owner
                val localCameraProvider = cameraProviderFuture.get()

                // Unbind all use cases before rebinding
                localCameraProvider.unbindAll()

                // Prepare the camera preview use case
                preview = Preview.Builder()
                    .setTargetAspectRatio(aspectRatio) // set the camera aspect ratio
                    .setTargetRotation(rotation) // set the camera rotation
                    .build()

                // Prepare the image capture use case
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(CAPTURE_MODE_MAXIMIZE_QUALITY) // setting to have pictures with highest quality possible (may be slow)
                    .setFlashMode(flashMode) // set capture flash
                    .setTargetAspectRatio(aspectRatio) // set the capture aspect ratio
                    .setTargetRotation(rotation) // set the capture rotation
                    .build()

                checkForHdrExtensionAvailability()

                // Optionally, prepare the image analysis use case
                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(aspectRatio) // set the analyzer aspect ratio
                    .setTargetRotation(rotation) // set the analyzer rotation
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // in our analysis, we care about the latest image
                    .build()
                    .also {
                        setLuminosityAnalyzer(it)
                    }

                // Bind the camera use cases to the lifecycle owner within the application's process
                val camera = localCameraProvider.bindToLifecycle(
                    this@CameraFragment,
                    lensFacing, // This should be the variable you've defined earlier
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

                // Retrieve the CameraControl and CameraInfo instances
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo

                // Attach the camera preview use case to the PreviewView
                preview?.setSurfaceProvider(viewFinder.surfaceProvider)

                // If you have additional setup for HDR or other camera features, include it here

            } catch (e: InterruptedException) {
                Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            } catch (e: ExecutionException) {
                Toast.makeText(requireContext(), "Error starting camera", Toast.LENGTH_SHORT).show()
                return@addListener
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun checkForHdrExtensionAvailability() {
        // Create a Vendor Extension for HDR
        val extensionsManagerFuture = ExtensionsManager.getInstanceAsync(
            requireContext(), cameraProvider ?: return,
        )
        extensionsManagerFuture.addListener(
            {
                val extensionsManager = extensionsManagerFuture.get() ?: return@addListener
                val cameraProvider = cameraProvider ?: return@addListener

                val isAvailable = extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR)

                // check for any extension availability
                println("AUTO " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.AUTO))
                println("HDR " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.HDR))
                println("FACE RETOUCH " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.FACE_RETOUCH))
                println("BOKEH " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.BOKEH))
                println("NIGHT " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NIGHT))
                println("NONE " + extensionsManager.isExtensionAvailable(lensFacing, ExtensionMode.NONE))

                // Check if the extension is available on the device
                if (!isAvailable) {
                    // If not, hide the HDR button
                    binding.btnHdr.visibility = View.GONE
                } else if (hasHdr) {
                    // If yes, turn on if the HDR is turned on by the user
                    binding.btnHdr.visibility = View.VISIBLE
                    hdrCameraSelector =
                        extensionsManager.getExtensionEnabledCameraSelector(lensFacing, ExtensionMode.HDR)
                }
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun setLuminosityAnalyzer(imageAnalysis: ImageAnalysis) {
        // Use a worker thread for image analysis to prevent glitches
        val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
        imageAnalysis.setAnalyzer(
            ThreadExecutor(Handler(analyzerThread.looper)),
            LuminosityAnalyzer()
        )
    }

    private fun bindToLifecycle(localCameraProvider: ProcessCameraProvider, viewFinder: PreviewView) {
        try {
            localCameraProvider.bindToLifecycle(
                viewLifecycleOwner, // current lifecycle owner
                hdrCameraSelector ?: lensFacing, // either front or back facing
                preview, // camera preview use case
                imageCapture, // image capture use case
                imageAnalyzer, // image analyzer use case
            ).run {
                // Init camera exposure control
                cameraInfo.exposureState.run {
                    val lower = exposureCompensationRange.lower
                    val upper = exposureCompensationRange.upper

                    binding.sliderExposure.run {
                        valueFrom = lower.toFloat()
                        valueTo = upper.toFloat()
                        stepSize = 1f
                        value = exposureCompensationIndex.toFloat()

                        addOnChangeListener { _, value, _ ->
                            cameraControl.setExposureCompensationIndex(value.toInt())
                        }
                    }
                }
            }

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind use cases", e)
        }
    }

    /**
     *  Detecting the most suitable aspect ratio for current dimensions
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    @Suppress("NON_EXHAUSTIVE_WHEN")
    private fun takePicture() = lifecycleScope.launch(Dispatchers.Main) {
        // Show a timer based on user selection
        when (selectedTimer) {
            CameraTimer.S3 -> for (i in 3 downTo 1) {
                binding.tvCountDown.text = i.toString()
                delay(1000)
            }
            CameraTimer.S10 -> for (i in 10 downTo 1) {
                binding.tvCountDown.text = i.toString()
                delay(1000)
            }
            CameraTimer.OFF -> {}
        }
        binding.tvCountDown.text = ""
        captureImage()
    }

    private fun captureImage() {
        val localImageCapture = imageCapture ?: throw IllegalStateException("Camera initialization failed.")

        // Setup image capture metadata
        val metadata = Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA
        }
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Options fot the output image file
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            val contentResolver = requireContext().contentResolver

            // Create the output uri
            val contentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
        } else {
            File(outputDirectory).mkdirs()
            val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

            OutputFileOptions.Builder(file)
        }.setMetadata(metadata).build()

        localImageCapture.takePicture(
            outputOptions, // the options needed for the final image
            requireContext().mainExecutor(), // the executor, on which the task will run
            object : OnImageSavedCallback { // the callback, about the result of capture process
                override fun onImageSaved(outputFileResults: OutputFileResults) {
                    // This function is called if capture is successfully completed
                    outputFileResults.savedUri
                        ?.let { uri ->
                            setGalleryThumbnail(uri)
                            Log.d(TAG, "Photo saved in $uri")
                        }
                        ?: setLastPictureThumbnail()
                }

                override fun onError(exception: ImageCaptureException) {
                    // This function is called if there is an errors during capture process
                    val msg = "Photo capture failed: ${exception.message}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.e(TAG, msg)
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun setGalleryThumbnail(savedUri: Uri?) = binding.btnGallery.load(savedUri) {
        placeholder(R.drawable.ic_no_picture)
        transformations(CircleCropTransformation())
        listener(object : ImageRequest.Listener {
            override fun onError(request: ImageRequest, result: ErrorResult) {
                super.onError(request, result)
                binding.btnGallery.load(savedUri) {
                    placeholder(R.drawable.ic_no_picture)
                    transformations(CircleCropTransformation())
//                    fetcher(VideoFrameUriFetcher(requireContext()))
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onBackPressed() = when {
        binding.llTimerOptions.visibility == View.VISIBLE -> binding.llTimerOptions.circularClose(binding.btnTimer)
        binding.llFlashOptions.visibility == View.VISIBLE -> binding.llFlashOptions.circularClose(binding.btnFlash)
        else -> requireActivity().finish()
    }

    companion object {
        private const val TAG = "CameraXDemo"

        const val KEY_FLASH = "sPrefFlashCamera"
        const val KEY_GRID = "sPrefGridCamera"
        const val KEY_HDR = "sPrefHDR"

        private const val IMAGE_PICK_CODE = 1000

        private const val RATIO_4_3_VALUE = 4.0 / 3.0 // aspect ratio 4x3
        private const val RATIO_16_9_VALUE = 16.0 / 9.0 // aspect ratio 16x9
    }
}
