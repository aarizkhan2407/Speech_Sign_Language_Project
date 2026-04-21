package com.example.speech_sign_language_project

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import java.lang.StringBuilder

class SignToSpeechViewModel : ViewModel() {
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
        wordBuilder.append(letter)
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
