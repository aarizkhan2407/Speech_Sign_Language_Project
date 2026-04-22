package com.example.speech_sign_language_project

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.speech_sign_language_project.ui.theme.SignBuddyTheme
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                viewModel.listeningStatus = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Throttle updates to improve performance
                if (kotlin.math.abs(rmsdB - viewModel.audioVolume) > 1.0f) {
                    viewModel.audioVolume = rmsdB
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // We keep isListening true until onResults or manual cancel
            }
            override fun onError(error: Int) {
                viewModel.audioVolume = 0f
                if (viewModel.isListening) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (viewModel.isListening) startListening(isRestart = true)
                    }, 500)
                } else {
                    viewModel.listeningStatus = ""
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    viewModel.audioVolume = 0f
                    // Append with space if not empty
                    viewModel.accumulatedText += (if (viewModel.accumulatedText.isNotEmpty()) " " else "") + text
                    viewModel.recognizedText = viewModel.accumulatedText
                    
                    if (viewModel.isListening) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (viewModel.isListening) startListening(isRestart = true)
                        }, 500)
                    }
                } else if (viewModel.isListening) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (viewModel.isListening) startListening(isRestart = true)
                    }, 500)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    viewModel.listeningStatus = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val gifImageLoader = ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        setContent {
            SignBuddyTheme {
                PhoneScreen(viewModel, gifImageLoader)
            }
        }
    }

    @Composable
    fun PhoneScreen(viewModel: MainViewModel, gifImageLoader: ImageLoader) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val context = LocalContext.current
        var textInput by remember { mutableStateOf("") }

        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC)).padding(16.dp)) {
            val constraints = this
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Info action */ }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF64748B))
                    }
                    Text(
                        "Sign Buddy",
                        fontSize = if (constraints.maxWidth < 600.dp) 24.sp else 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, SignToSpeechActivity::class.java))
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Sign Detection", tint = Color(0xFF64748B))
                    }
                }



                // Input Section (Spoken text or manual entry)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (viewModel.isListening) {
                            ListeningCard(viewModel.listeningStatus.ifEmpty { "Listening..." })
                        } else {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Type something to translate...", fontSize = 14.sp) },
                                trailingIcon = {
                                    if (textInput.isNotEmpty()) {
                                        IconButton(onClick = { 
                                            viewModel.translateAndPlay(context, textInput)
                                            textInput = ""
                                        }) {
                                            Icon(Icons.Default.Send, contentDescription = "Translate", tint = Color(0xFF3B82F6))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color(0xFFE2E8F0)
                                )
                            )
                        }
                        
                        if (viewModel.recognizedText.isNotEmpty() && !viewModel.isListening) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RecognizedTextDisplay(viewModel.recognizedText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preview Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            SignPreview(viewModel.signSequence, viewModel.currentIndex, viewModel.isPaused, gifImageLoader, viewModel.playbackSessionKey)
                        }
                        if (viewModel.signSequence.isNotEmpty()) {
                            PlaybackControls(
                                isPaused = viewModel.isPaused,
                                isLooping = viewModel.isLooping,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onReplay = { viewModel.startPlayback(viewModel.signSequence) },
                                onToggleLoop = { viewModel.isLooping = !viewModel.isLooping }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Controls (Mic and Restart)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Restart Button
                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Mic Button
                    MicButton(
                        isListening = viewModel.isListening,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                            } else {
                                if (viewModel.isListening) {
                                    speechRecognizer.stopListening()
                                    viewModel.isListening = false
                                    viewModel.translateAndPlay(context, viewModel.accumulatedText)
                                } else {
                                    startListening(isRestart = false)
                                }
                            }
                        },
                        modifier = Modifier.size(72.dp),
                        volume = viewModel.audioVolume
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // Spacer to balance the layout
                    Box(modifier = Modifier.size(56.dp))
                }
            }
        }
    }

    private fun startListening(isRestart: Boolean = false) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        viewModel.isListening = true
        if (!isRestart) {
            viewModel.accumulatedText = ""
            viewModel.recognizedText = ""
        }
        speechRecognizer.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
}
