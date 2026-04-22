package com.example.speech_sign_language_project

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Size as SizeCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.animateFloatAsState
import androidx.core.content.ContextCompat
import com.example.speech_sign_language_project.ui.theme.SignBuddyTheme
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SignToSpeechActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: SignToSpeechViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var tts: TextToSpeech? = null

    private var stableCount = 0
    private var lastDetected = "?"
    private var lastAddedSign = ""
    private val STABLE_FRAMES = 30
    private val CONFIDENCE_THRESHOLD = 0.60f
    private var frameCount = 0
    private var hasCameraPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hasCameraPermission = true
            // Re-compose to start camera
            setContent {
                SignBuddyTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F5F9)) {
                        SignToSpeechScreen(viewModel)
                    }
                }
            }
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        TFLiteClassifier.init(this)
        tts = TextToSpeech(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            SignBuddyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F5F9)) {
                    SignToSpeechScreen(viewModel)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SignToSpeechScreen(viewModel: SignToSpeechViewModel) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            CenterAlignedTopAppBar(
                title = { Text("Sign Detection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.toggleCamera() }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera")
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                // Camera Preview
                val previewView = remember {
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }

                LaunchedEffect(viewModel.lensFacing, hasCameraPermission) {
                    if (hasCameraPermission) {
                        startCamera(previewView, lifecycleOwner, viewModel.cameraSelector)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Visual Guide Frame (Hand Focus Square)
                        Box(
                            modifier = Modifier
                                .size(280.dp)
                                .align(Alignment.Center)
                                .background(Color.Transparent)
                                .drawWithContent {
                                    drawContent()
                                    val strokeWidth = 4.dp.toPx()
                                    val cornerLen = 40.dp.toPx()
                                    val color = if (viewModel.holdProgress >= 100) Color.Green else Color.White.copy(alpha = 0.5f)
                                    
                                    // Top Left
                                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(cornerLen, 0f), strokeWidth)
                                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, cornerLen), strokeWidth)
                                    
                                    // Top Right
                                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - cornerLen, 0f), strokeWidth)
                                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, cornerLen), strokeWidth)
                                    
                                    // Bottom Left
                                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(cornerLen, size.height), strokeWidth)
                                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - cornerLen), strokeWidth)
                                    
                                    // Bottom Right
                                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - cornerLen, size.height), strokeWidth)
                                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLen), strokeWidth)
                                }
                        )

                        Text(
                            "Put hand inside frame",
                            modifier = Modifier.align(Alignment.Center).padding(top = 320.dp),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }

                // Detection Overlay (bottom of card)
                DetectionOverlay(viewModel)

                if (!hasCameraPermission) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f))) {
                        Text(
                            "Camera permission required",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Word Builder Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Built Word:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        text = viewModel.builtWord.ifEmpty { "..." },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (viewModel.builtWord.isNotEmpty()) speakOut(viewModel.builtWord)
                                else Toast.makeText(context, "Nothing to speak", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Speak")
                        }

                        OutlinedButton(
                            onClick = { viewModel.clear() },
                            modifier = Modifier.weight(0.6f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    private fun BoxScope.DetectionOverlay(viewModel: SignToSpeechViewModel) {
        val animatedProgress by animateFloatAsState(targetValue = viewModel.holdProgress / 100f)

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Detected: ${viewModel.detectedSign}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (viewModel.holdProgress >= 100) Color.Green else Color.Yellow)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (viewModel.holdProgress >= 100) Color.Green else Color(0xFF3B82F6),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }

    private fun startCamera(previewView: PreviewView, lifecycleOwner: androidx.lifecycle.LifecycleOwner, cameraSelector: CameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(SizeCompat(720, 1280))
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("SignToSpeech", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        frameCount = (frameCount + 1) % 3
        if (frameCount != 0) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val argbBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            
            // Mirror only for front camera
            val matrix = Matrix().apply { 
                if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    preScale(-1f, 1f) 
                }
            }
            val processedBitmap = if (viewModel.lensFacing == CameraSelector.LENS_FACING_FRONT) {
                Bitmap.createBitmap(argbBitmap, 0, 0, argbBitmap.width, argbBitmap.height, matrix, false)
            } else {
                argbBitmap
            }

            val (sign, confidence) = TFLiteClassifier.classify(processedBitmap)
            updateSignLogic(sign, confidence)

        } catch (e: Exception) {
            Log.e("SignToSpeech", "Frame error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun updateSignLogic(sign: String, confidence: Float) {
        val filteredSign = when (sign) {
            "nothing" -> "?"
            else -> sign
        }

        val validSign = if (confidence >= CONFIDENCE_THRESHOLD) filteredSign else "?"

        if (validSign == lastDetected && validSign != "?") {
            stableCount++
        } else {
            stableCount = 0
            lastDetected = validSign
        }

        val progress = (stableCount.toFloat() / STABLE_FRAMES * 100).toInt().coerceAtMost(100)
        val confPct = (confidence * 100).toInt()

        runOnUiThread {
            viewModel.updateDetectedSign(validSign, confPct, progress, stableCount >= STABLE_FRAMES)
            
            if (stableCount == STABLE_FRAMES && validSign != lastAddedSign && validSign != "?") {
                lastAddedSign = validSign
                stableCount = 0
                viewModel.appendLetter(validSign)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        TFLiteClassifier.close()
        tts?.stop()
        tts?.shutdown()
    }
}