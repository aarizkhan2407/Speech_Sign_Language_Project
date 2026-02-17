package com.example.speech_sign_language_project

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// Represents one ISL sign (GIF or letter image)
data class SignItem(val assetPath: String)

class MainActivity : ComponentActivity() {

    private var onSpeechResult: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ISLApp() }
    }

    // ---------------- SPEECH ----------------

    private fun startSpeechRecognition(
        context: Context,
        onResult: (String) -> Unit
    ) {
        onSpeechResult = onResult

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale("en", "IN")
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak now"
            )
        }

        (context as Activity).startActivityForResult(intent, 1)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            val text = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?: ""

            onSpeechResult?.invoke(text)
        }
    }

    // ---------------- UI ----------------

    @Composable
    fun ISLApp() {

        val context = LocalContext.current

        var recognizedText by remember { mutableStateOf("") }
        var signSequence by remember { mutableStateOf<List<SignItem>>(emptyList()) }
        var currentIndex by remember { mutableStateOf(0) }

        // Coroutine scope + playback job (CRASH FIX)
        val scope = rememberCoroutineScope()
        var playbackJob by remember { mutableStateOf<Job?>(null) }

        // GIF-capable ImageLoader
        val gifImageLoader = remember {
            ImageLoader.Builder(context)
                .components {
                    add(GifDecoder.Factory())
                }
                .build()
        }

        // Safe playback function
        fun startPlayback(sequence: List<SignItem>) {

            playbackJob?.cancel() // stop old playback safely

            playbackJob = scope.launch {

                if (sequence.isEmpty()) return@launch

                for (i in sequence.indices) {

                    currentIndex = i

                    val path = sequence[i].assetPath

                    if (path.endsWith(".gif")) {
                        delay(2500) // allow full GIF playback
                    } else {
                        delay(800) // letters
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF5F7FA),
                            Color(0xFFE4ECF7)
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Speech to Indian Sign Language",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ISL DISPLAY AREA
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .background(
                        color = Color(0xFFEAF2FF),
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {

                if (signSequence.isNotEmpty()) {

                    AsyncImage(
                        model = "file:///android_asset/${signSequence[currentIndex].assetPath}",
                        imageLoader = gifImageLoader,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )

                } else {

                    Text(
                        text = "ISL Output",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            FloatingActionButton(
                containerColor = Color(0xFF6366F1),
                onClick = {

                    startSpeechRecognition(context) { text ->

                        recognizedText = text

                        val newSequence = convertTextToISL(text)

                        signSequence = newSequence

                        startPlayback(newSequence) // SAFE playback start
                    }
                }
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Speak",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFDCEAFE)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Recognized Speech",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E40AF)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (recognizedText.isBlank()) "—" else recognizedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }

    // ---------------- ISL LOGIC ----------------

    private fun convertTextToISL(text: String): List<SignItem> {

        val islWordMap = mapOf(
            "hello" to "gifs/hello.gif",
            "good" to "gifs/good.gif",
            "morning" to "gifs/morning.gif",
            "you" to "gifs/you.gif",
        )

        val cleanedWords = text
            .lowercase()
            .replace(Regex("[^a-z ]"), "")
            .split(" ")
            .filter { it.isNotBlank() }

        val signs = mutableListOf<SignItem>()

        for (word in cleanedWords) {

            if (islWordMap.containsKey(word)) {

                signs.add(SignItem(islWordMap[word]!!))

            } else {

                for (ch in word) {

                    signs.add(SignItem("letters/${ch}.png"))
                }
            }
        }

        return signs
    }
}