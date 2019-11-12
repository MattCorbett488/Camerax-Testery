package com.willowtreeapps.cameraxtest

import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import com.willowtreeapps.cameraxtest.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        if (allPermissionsGranted()) {
            binding.viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        binding.viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTransform() }
    }

    private fun startCamera() {
        binding.viewFinder.doOnPreDraw { view ->
            val isLandscape = when (binding.viewFinder.display.rotation) {
                Surface.ROTATION_90, Surface.ROTATION_270 -> true
                else -> false
            }
            val aspectRatio = if (isLandscape) {
                Rational(view.height, view.width)
            } else {
                Rational(view.width, view.height)
            }

            val previewConfig = PreviewConfig.Builder().apply {
                //setTargetAspectRatioCustom(aspectRatio)
                //setTargetAspectRatio(AspectRatio.RATIO_16_9)
                setTargetResolution(Size(1920, 1080))
            }.build()

            val preview = Preview(previewConfig)
            preview.setOnPreviewOutputUpdateListener { previewOutput ->
                //Update SurfaceTexture by removing and re-adding
                val parent = binding.viewFinder.parent as ViewGroup
                parent.removeView(binding.viewFinder)
                parent.addView(binding.viewFinder, 0)

                binding.viewFinder.surfaceTexture = previewOutput.surfaceTexture
                updateTransform()
            }


            val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
                //setTargetAspectRatioCustom(aspectRatio)
                setTargetAspectRatio(AspectRatio.RATIO_16_9)

                // We don't set a resolution for image capture; instead, we
                // select a capture mode which will infer the appropriate
                // resolution based on aspect ratio and requested mode
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }.build()
            val imageCapture = ImageCapture(imageCaptureConfig)
            binding.captureButton.setOnClickListener {
                val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")

                imageCapture.takePicture(file,
                    { command -> runOnUiThread(command) },
                    object : ImageCapture.OnImageSavedListener {
                        override fun onImageSaved(file: File) {
                            val msg = "Photo Capture succeeded: ${file.absolutePath}"
                            Toast.makeText(baseContext, msg, LENGTH_SHORT).show()
                            Log.d("CameraXTest", msg)
                        }

                        override fun onError(
                            imageCaptureError: ImageCapture.ImageCaptureError,
                            message: String,
                            cause: Throwable?
                        ) {
                            val msg = "Photo capture failed: $message"
                            Toast.makeText(baseContext, msg, LENGTH_SHORT).show()
                            Log.e("CameraXApp", msg)
                            cause?.printStackTrace()
                        }
                    }
                )
            }

            val analyzerConfig = ImageAnalysisConfig.Builder().apply {
                //Use a worker thread for analysis
                val analyzerThread = HandlerThread("LuminosityAnalysis").apply { start() }
                //setCallbackHandler(Handler(analyzerThread.looper))

                //Care more about latest rather than EVERY image
                setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            }.build()

            val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
                setAnalyzer(Executor { runnable -> runnable.run() }, LuminosityAnalyzer())
            }


            CameraX.bindToLifecycle(this, preview, imageCapture, analyzerUseCase)
        }
    }

    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = binding.viewFinder.width / 2f
        val centerY = binding.viewFinder.height / 2f

        //Correct preview to account for rotation
        val rotationDegrees = when (binding.viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        binding.viewFinder.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                binding.viewFinder.post { startCamera() }
            } else {
                Toast.makeText(this, "Permissions not granted by user.", LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}
