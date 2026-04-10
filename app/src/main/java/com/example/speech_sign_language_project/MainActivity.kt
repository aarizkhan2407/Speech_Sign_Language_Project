package com.example.speech_sign_language_project

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
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

import okhttp3.*
import org.json.JSONObject

data class SignItem(val assetPath: String)

class MainActivity : ComponentActivity() {

    private var onSpeechResult: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ISLApp() }
    }

    // ---------------- GOOGLE TRANSLATE API ----------------

    private fun translateToEnglishAPI(
        text: String,
        onTranslated: (String) -> Unit
    ) {
        val apiKey = "AIzaSyAyar9tQPfKsw2bsuDqswJdTRGVITDP4o4"

        val client = OkHttpClient()

        val requestBody = FormBody.Builder()
            .add("q", text)
            .add("source", "hi")
            .add("target", "en")
            .build()

        val request = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
            .post(requestBody)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                val json = response.body?.string()

                val translatedText = JSONObject(json)
                    .getJSONObject("data")
                    .getJSONArray("translations")
                    .getJSONObject(0)
                    .getString("translatedText")

                runOnUiThread {
                    onTranslated(translatedText)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    onTranslated(text)
                }
            }
        }.start()
    }

    // ---------------- SPEECH ----------------

    private fun startSpeechRecognition(
        context: Context,
        language: String,
        onResult: (String) -> Unit
    ) {
        onSpeechResult = onResult

        val langCode = if (language == "hi") "hi-IN" else "en-IN"

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
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

    // ---------------- DEVANAGARI CHECK ----------------

    private fun isDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    // ---------------- UI ----------------

    @Composable
    fun ISLApp() {

        val context = LocalContext.current

        var recognizedText by remember { mutableStateOf("") }
        var selectedLanguage by remember { mutableStateOf("en") }
        var signSequence by remember { mutableStateOf<List<SignItem>>(emptyList()) }
        var currentIndex by remember { mutableStateOf(0) }

        val scope = rememberCoroutineScope()
        var playbackJob by remember { mutableStateOf<Job?>(null) }

        val gifImageLoader = remember {
            ImageLoader.Builder(context)
                .components { add(GifDecoder.Factory()) }
                .build()
        }

        fun startPlayback(sequence: List<SignItem>) {
            playbackJob?.cancel()

            playbackJob = scope.launch {
                for (i in sequence.indices) {
                    currentIndex = i
                    val path = sequence[i].assetPath
                    delay(if (path.endsWith(".gif")) 2500 else 800)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFF5F7FA), Color(0xFFE4ECF7))
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("Speech to ISL", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(20.dp))

            // LANGUAGE TOGGLE
            Row {
                Button(
                    onClick = { selectedLanguage = "en" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedLanguage == "en") Color(0xFF6366F1) else Color.LightGray
                    )
                ) { Text("English") }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = { selectedLanguage = "hi" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedLanguage == "hi") Color(0xFF6366F1) else Color.LightGray
                    )
                ) { Text("Hindi") }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .background(Color(0xFFEAF2FF), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (signSequence.isNotEmpty()) {
                    AsyncImage(
                        model = "file:///android_asset/${signSequence[currentIndex].assetPath}",
                        imageLoader = gifImageLoader,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("ISL Output")
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            FloatingActionButton(
                onClick = {
                    startSpeechRecognition(context, selectedLanguage) { text ->

                        recognizedText = text

                        if (selectedLanguage == "hi") {

                            if (!isDevanagari(text)) {
                                Toast.makeText(context, "Please speak in Hindi", Toast.LENGTH_SHORT).show()
                                return@startSpeechRecognition
                            }

                            // 🔥 GOOGLE TRANSLATE HERE
                            translateToEnglishAPI(text) { translated ->

                                val sequence = convertTextToISL(translated)
                                signSequence = sequence
                                startPlayback(sequence)
                            }

                        } else {

                            val sequence = convertTextToISL(text)
                            signSequence = sequence
                            startPlayback(sequence)
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Mode: ${if (selectedLanguage == "hi") "Hindi" else "English"}")

            Spacer(modifier = Modifier.height(10.dp))

            Text("Text: $recognizedText")
        }
    }

    // ---------------- ISL LOGIC ----------------

    private fun convertTextToISL(text: String): List<SignItem> {

        val wordMap = mapOf(
            "hello" to "gifs/hello.gif",
            "good" to "gifs/good.gif",
            "morning" to "gifs/morning.gif",
            "you" to "gifs/you.gif",
            "fine" to "gifs/fine.gif"
        )

        val cleanedText = text.lowercase()
            .replace(Regex("[^a-z ]"), "")
            .trim()

        val words = cleanedText.split(" ").filter { it.isNotBlank() }

        val signs = mutableListOf<SignItem>()

        for (word in words) {
            if (wordMap.containsKey(word)) {
                signs.add(SignItem(wordMap[word]!!))
            } else {
                for (ch in word) {
                    signs.add(SignItem("letters/$ch.png"))
                }
            }
        }

        return signs
    }
}