package com.example.speech_sign_language_project

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.lang.StringBuilder

class SignToSpeechViewModel : ViewModel() {
    var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_FRONT)
    val cameraSelector: CameraSelector
        get() = CameraSelector.Builder().requireLensFacing(lensFacing).build()

    fun toggleCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
    }

    var detectedSign by mutableStateOf("?")
    var confidence by mutableStateOf(0)
    var builtWord by mutableStateOf("")
    var holdProgress by mutableStateOf(0)
    var isWordAdded by mutableStateOf(false)

    private val wordBuilder = StringBuilder()

    fun updateDetectedSign(sign: String, conf: Int, progress: Int, added: Boolean) {
        detectedSign = sign
        confidence = conf
        holdProgress = progress
        isWordAdded = added
    }

    fun appendLetter(letter: String) {
        when (letter.lowercase()) {
            "space" -> wordBuilder.append(" ")
            "del" -> if (wordBuilder.isNotEmpty()) wordBuilder.deleteCharAt(wordBuilder.length - 1)
            "nothing" -> { /* do nothing */ }
            else -> wordBuilder.append(letter)
        }
        builtWord = wordBuilder.toString()
    }

    fun clear() {
        wordBuilder.clear()
        builtWord = ""
        detectedSign = "?"
        confidence = 0
        holdProgress = 0
    }
}
