package com.example.speech_sign_language_project

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {

    // This variable stores the latest recognized speech text
    companion object {
        var recognizedText: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This sets the UI content of the app using Jetpack Compose
        setContent {
            SpeechToTextUI()
        }
    }

    // This function launches Android's built-in speech recognition system
    private fun startSpeechRecognition(context: Context) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        // Use free-form speech input
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        // Use device default language
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            Locale.getDefault()
        )

        // Prompt shown to user
        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "Speak now"
        )

        // Start speech recognizer
        (context as Activity).startActivityForResult(intent, 1)
    }

    // This method receives the recognized speech result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            val result =
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

            // Store the most confident recognized text
            recognizedText = result?.get(0) ?: ""
        }
    }

    // UI function that displays button and recognized text
    @Composable
    fun SpeechToTextUI() {

        val context = LocalContext.current

        // This state variable updates UI when recognizedText changes
        var displayText by remember { mutableStateOf("Press Speak and talk") }

        // Update UI whenever speech result changes
        LaunchedEffect(recognizedText) {
            if (recognizedText.isNotEmpty()) {
                displayText = recognizedText
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Button(onClick = {
                startSpeechRecognition(context)
            }) {
                Text(text = "Speak")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = displayText,
                fontSize = 22.sp
            )
        }
    }
}
