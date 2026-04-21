package com.example.speech_sign_language_project

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SignToSpeechActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var tvDetectedLetter: TextView
    private lateinit var tvBuiltWord: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var btnSpeak: Button
    private lateinit var btnClear: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tts: TextToSpeech

    private val builtWord            = StringBuilder()
    private var lastDetected         = "?"
    private var stableCount          = 0
    private val STABLE_FRAMES        = 40      // ~4 seconds of holding
    private var lastAddedSign        = ""
    private val CONFIDENCE_THRESHOLD = 0.70f   // high confidence required
    private val mainHandler          = Handler(Looper.getMainLooper())

    // Frame skip — only process every 3rd frame
    private var frameCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e("STARTUP", "=== SignToSpeechActivity onCreate START ===")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_to_speech)

        previewView      = findViewById(R.id.cameraPreview)
        tvDetectedLetter = findViewById(R.id.tvDetectedLetter)
        tvBuiltWord      = findViewById(R.id.tvBuiltWord)
        tvConfidence     = findViewById(R.id.tvConfidence)
        btnSpeak         = findViewById(R.id.btnSpeak)
        btnClear         = findViewById(R.id.btnClear)

        TFLiteClassifier.init(this)
        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        btnSpeak.setOnClickListener {
            val text = builtWord.toString().trim()
            if (text.isNotEmpty()) speakOut(text)
            else Toast.makeText(this, "Nothing to speak yet!", Toast.LENGTH_SHORT).show()
        }

        btnClear.setOnClickListener {
            builtWord.clear()
            lastAddedSign = ""
            tvBuiltWord.text = ""
            tvConfidence.text = "Cleared!"
        }

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("SignToSpeech", "Camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        // Skip every 2 out of 3 frames — reduces jitter and speeds up stability
        frameCount++
        if (frameCount % 3 != 0) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()

            val argbBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }

            val matrix = Matrix().apply { preScale(-1f, 1f) }
            val mirrored = Bitmap.createBitmap(
                argbBitmap, 0, 0,
                argbBitmap.width, argbBitmap.height,
                matrix, false
            )

            val (sign, confidence) = TFLiteClassifier.classify(mirrored)

            Log.d("TFLite", "Sign=$sign Conf=${"%.2f".format(confidence)}")

            updateSignState(sign, confidence)

        } catch (e: Exception) {
            Log.e("SignToSpeech", "Frame error: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun updateSignState(sign: String, confidence: Float) {
        // Filter out biased/noise signs
        val filteredSign = when (sign) {
            "nothing", "space", "del" -> "?"
            else -> sign
        }

        val validSign = if (confidence >= CONFIDENCE_THRESHOLD) filteredSign else "?"

        if (validSign == lastDetected && validSign != "?") {
            stableCount++
        } else {
            stableCount  = 0
            lastDetected = validSign
        }

        val progress = (stableCount.toFloat() / STABLE_FRAMES * 100).toInt().coerceAtMost(100)
        val confPct  = (confidence * 100).toInt()

        mainHandler.post {
            tvDetectedLetter.text = if (validSign == "?") "?" else validSign

            tvConfidence.text = when {
                validSign == "?"             -> "No sign ($confPct%)"
                stableCount >= STABLE_FRAMES -> "✓ Added!"
                else                         -> "Conf: $confPct% | Hold: $progress%"
            }

            if (stableCount == STABLE_FRAMES && validSign != lastAddedSign) {
                lastAddedSign = validSign
                stableCount   = 0
                builtWord.append(validSign)
                tvBuiltWord.text = builtWord.toString()
                Log.e("SignToSpeech", "Added '$validSign' → '${builtWord}'")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            Log.d("SignToSpeech", "TTS ready")
        }
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        TFLiteClassifier.close()
        tts.stop()
        tts.shutdown()
    }
}