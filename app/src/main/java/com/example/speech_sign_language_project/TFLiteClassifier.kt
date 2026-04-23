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
                .setNumHands(1)
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

            // MediaPipe detection result
            val landmarks = result.landmarks()[0]
            
            // Basic landmark processing without handedness check for now to fix build
            // Dataset structure: [L_x1, L_y1... (42 total), R_x1, R_y1... (42 total)]
            // Defaulting to "Right hand" slot (offset 42) for single-hand detection
            val isLeft = false 


            inputBuffer.rewind()
            val features = FloatArray(84) { 0f }
            
            // Dataset structure: [L_x1, L_y1... (42 total), R_x1, R_y1... (42 total)]
            val offset = if (isLeft) 0 else 42
            for (i in 0 until 21) {
                if (i < landmarks.size) {
                    val lm = landmarks[i]
                    features[offset + (i * 2)] = lm.x()
                    features[offset + (i * 2) + 1] = lm.y()
                }
            }

            for (f in features) {
                inputBuffer.putFloat(f)
            }

            interp.run(inputBuffer, outputBuffer)

            val scores = outputBuffer[0]
            val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
            val confidence = if (maxIdx != -1) scores[maxIdx] else 0f
            val label = labels[maxIdx] ?: "?"

            Pair(label, confidence)
        } catch (e: Exception) {
            Log.e("TFLite", "Classify error", e)
            Pair("?", 0f)
        }
    }

    fun close() {
        interpreter?.close()
        handLandmarker?.close()
        interpreter = null
        handLandmarker = null
    }
}