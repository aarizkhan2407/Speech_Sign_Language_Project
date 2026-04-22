package com.example.speech_sign_language_project

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainViewModel : ViewModel() {

    var recognizedText by mutableStateOf("")
    var accumulatedText by mutableStateOf("")
    var signSequence by mutableStateOf<List<SignItem>>(emptyList())
    var currentIndex by mutableStateOf(0)
    var playbackSessionKey by mutableStateOf(0)
    
    var isListening by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var isLooping by mutableStateOf(false)
    var listeningStatus by mutableStateOf("Listening...")
    var audioVolume by mutableStateOf(0f)

    private var playbackJob: Job? = null
    private val client = OkHttpClient()

    fun startPlayback(sequence: List<SignItem>) {
        playbackJob?.cancel()
        signSequence = sequence
        isPaused = false
        currentIndex = 0
        playbackSessionKey++
        
        if (sequence.isEmpty()) return

        playbackJob = viewModelScope.launch {
            while (isActive) {
                if (!isPaused) {
                    if (currentIndex < sequence.size - 1) {
                        delay(2000)
                        currentIndex++
                    } else {
                        if (isLooping) {
                            delay(2000)
                            currentIndex = 0
                            playbackSessionKey++
                        } else {
                            isPaused = true
                            break
                        }
                    }
                } else {
                    delay(100)
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        signSequence = emptyList()
        isPaused = false
        isLooping = false
    }

    fun translateAndPlay(context: Context, text: String) {
        val apiKey = context.getString(R.string.translate_api_key)
        
        val requestBody = FormBody.Builder()
            .add("q", text)
            .add("target", "en")
            .build()

        val request = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
            .post(requestBody)
            .build()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val json = response.body?.string()

                if (json != null) {
                    val translatedText = JSONObject(json)
                        .getJSONObject("data")
                        .getJSONArray("translations")
                        .getJSONObject(0)
                        .getString("translatedText")

                    withContext(Dispatchers.Main) {
                        recognizedText = translatedText
                        val sequence = convertTextToISL(translatedText)
                        signSequence = sequence
                        startPlayback(sequence)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    recognizedText = text
                    val sequence = convertTextToISL(text)
                    signSequence = sequence
                    startPlayback(sequence)
                }
            }
        }
    }
    
    fun togglePlayPause() {
        if (currentIndex >= signSequence.size - 1 && isPaused) {
            startPlayback(signSequence)
        } else {
            isPaused = !isPaused
        }
    }

    fun reset() {
        recognizedText = ""
        accumulatedText = ""
        signSequence = emptyList()
        isPaused = false
        isLooping = false
        playbackSessionKey = 0
        playbackJob?.cancel()
    }
}
