package com.example.speech_sign_language_project

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object TFLiteClassifier {

    private var interpreter: Interpreter? = null
    private var handLandmarker: HandLandmarker? = null
    private var labels: Map<Int, String> = emptyMap()
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputBuffer: Array<FloatArray>

    fun init(context: Context) {
        if (interpreter != null) return
        Log.e("TFLite", "=== GESTURE MODEL INIT ===")
        try {
            // 1. MediaPipe Hand Landmarker
            val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)

            handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())

            // 2. Gesture Model TFLite
            val fd = context.assets.openFd("gesture_model.tflite")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val modelBuffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
            interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2))

            // 3. Labels
            val labelsJson = context.assets.open("gesture_labels.json").bufferedReader().readText()
            val jsonObj = JSONObject(labelsJson)
            val labelMap = mutableMapOf<Int, String>()
            jsonObj.keys().forEach { key ->
                labelMap[key.toInt()] = jsonObj.getString(key)
            }
            labels = labelMap

            // 4. Buffers
            inputBuffer = ByteBuffer.allocateDirect(4 * 84).apply {
                order(ByteOrder.nativeOrder())
            }
            outputBuffer = Array(1) { FloatArray(labels.size) }

            Log.e("TFLite", "INIT SUCCESS")
        } catch (e: Exception) {
            Log.e("TFLite", "INIT ERROR", e)
        }
    }

    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val interp = interpreter ?: return Pair("?", 0f)
        val landmarker = handLandmarker ?: return Pair("?", 0f)

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: HandLandmarkerResult = landmarker.detect(mpImage)

            if (result.landmarks().isEmpty()) {
                return Pair("Nothing", 0.0f)
            }

            inputBuffer.rewind()
            
            // Perspective 1: Direct Mapping
            val directFeatures = FloatArray(84) { 0f }
            val firstHand = result.landmarks()[0]
            val firstHandedness = result.handednesses()[0][0].categoryName()
            val directOffset = if (firstHandedness == "Left") 0 else 42
            
            for (i in 0 until 21) {
                directFeatures[directOffset + (i * 2)] = firstHand[i].x()
                directFeatures[directOffset + (i * 2) + 1] = firstHand[i].y()
            }
            
            val directResult = runInference(directFeatures)
            
            // Perspective 2: Mirrored Mapping (x = 1-x) and Swapped Slot
            val mirrorFeatures = FloatArray(84) { 0f }
            val mirrorOffset = if (firstHandedness == "Left") 42 else 0 // Swap slot
            
            for (i in 0 until 21) {
                mirrorFeatures[mirrorOffset + (i * 2)] = 1.0f - firstHand[i].x() // Mirror x
                mirrorFeatures[mirrorOffset + (i * 2) + 1] = firstHand[i].y()
            }
            
            val mirrorResult = runInference(mirrorFeatures)
            
            // Choose the best one
            val finalResult = if (directResult.second >= mirrorResult.second) directResult else mirrorResult
            
            Log.d("TFLite", "Perspectives - Direct: ${directResult.first}(${directResult.second}), Mirror: ${mirrorResult.first}(${mirrorResult.second})")
            Log.d("TFLite", "Final Selection: ${finalResult.first} (Hand: $firstHandedness)")
            
            finalResult
        } catch (e: Exception) {
            Log.e("TFLite", "Classify error", e)
            Pair("?", 0f)
        }
    }

    private fun runInference(features: FloatArray): Pair<String, Float> {
        val interp = interpreter ?: return Pair("?", 0f)
        inputBuffer.rewind()
        for (f in features) {
            inputBuffer.putFloat(f)
        }
        
        val localOutput = Array(1) { FloatArray(labels.size) }
        interp.run(inputBuffer, localOutput)
        
        val scores = localOutput[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
        val confidence = if (maxIdx != -1) scores[maxIdx] else 0f
        val label = labels[maxIdx] ?: "?"
        
        return Pair(label, confidence)
    }

    fun close() {
        interpreter?.close()
        handLandmarker?.close()
        interpreter = null
        handLandmarker = null
    }
}