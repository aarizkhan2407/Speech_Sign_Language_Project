package com.example.speech_sign_language_project

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object TFLiteClassifier {

    private var interpreter: Interpreter? = null
    private var labels: Map<Int, String> = emptyMap()
    private var imgSize: Int = 96

    fun init(context: Context) {
        if (interpreter != null) return
        Log.e("TFLite", "=== INIT CALLED ===")
        try {
            Log.e("TFLite", "Step 1: listing assets...")
            val list = context.assets.list("") ?: emptyArray()
            Log.e("TFLite", "Step 1 OK - assets: ${list.toList()}")

            Log.e("TFLite", "Step 2: checking model file exists...")
            if ("asl_model.tflite" !in list) {
                Log.e("TFLite", "FATAL: asl_model.tflite NOT in assets!")
                return
            }
            Log.e("TFLite", "Step 2 OK - model file found")

            Log.e("TFLite", "Step 3: opening model file descriptor...")
            val fd = context.assets.openFd("asl_model.tflite")
            Log.e("TFLite", "Step 3 OK - fd length=${fd.length} declared=${fd.declaredLength}")

            Log.e("TFLite", "Step 4: mapping model to buffer...")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val modelBuffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
            Log.e("TFLite", "Step 4 OK - buffer capacity=${modelBuffer.capacity()}")

            Log.e("TFLite", "Step 5: creating Interpreter...")
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.e("TFLite", "Step 5 OK - interpreter created")

            Log.e("TFLite", "Step 6: reading tensor shapes...")
            val inputShape = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            imgSize = inputShape[1]
            Log.e("TFLite", "Step 6 OK - input=${inputShape.toList()} output=${outputShape.toList()} imgSize=$imgSize")

            Log.e("TFLite", "Step 7: loading labels...")
            val labelsJson = context.assets.open("asl_labels.json").bufferedReader().readText()
            Log.e("TFLite", "Step 7 OK - json length=${labelsJson.length}")

            Log.e("TFLite", "Step 8: parsing labels...")
            val jsonObj = JSONObject(labelsJson)
            labels = jsonObj.keys().asSequence()
                .associate { it.toInt() to jsonObj.getString(it) }
            Log.e("TFLite", "Step 8 OK - ${labels.size} labels loaded")

            Log.e("TFLite", "=== INIT COMPLETE SUCCESSFULLY ===")

        } catch (e: Exception) {
            Log.e("TFLite", "=== INIT EXCEPTION ===")
            Log.e("TFLite", "Type: ${e.javaClass.name}")
            Log.e("TFLite", "Message: ${e.message}")
            Log.e("TFLite", "Stack:", e)
        }
    }

    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val interp = interpreter ?: run {
            Log.e("TFLite", "classify() - interpreter is null!")
            return Pair("?", 0f)
        }

        if (labels.isEmpty()) {
            Log.e("TFLite", "classify() - labels is empty!")
            return Pair("?", 0f)
        }

        return try {
            // ── Step 1: Crop center square ────────────────────────────────
            // Training data was square images — crop center before resizing
            // to avoid squishing rectangular camera frames
            val size = minOf(bitmap.width, bitmap.height)
            val xOffset = (bitmap.width - size) / 2
            val yOffset = (bitmap.height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

            // ── Step 2: Resize to model input size ────────────────────────
            val resized = Bitmap.createScaledBitmap(cropped, imgSize, imgSize, true)

            // ── Step 3: Convert to float ByteBuffer (RGB normalized 0-1) ──
            val byteBuffer = ByteBuffer
                .allocateDirect(4 * imgSize * imgSize * 3)
                .apply { order(ByteOrder.nativeOrder()) }

            val pixels = IntArray(imgSize * imgSize)
            resized.getPixels(pixels, 0, imgSize, 0, 0, imgSize, imgSize)
            for (pixel in pixels) {
                byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                byteBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255f) // G
                byteBuffer.putFloat((pixel           and 0xFF) / 255f) // B
            }

            // ── Step 4: Run inference ─────────────────────────────────────
            val outputSize = interp.getOutputTensor(0).shape()[1]
            val output = Array(1) { FloatArray(outputSize) }
            interp.run(byteBuffer, output)

            // ── Step 5: Get top result ────────────────────────────────────
            val scores = output[0]
            val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: return Pair("?", 0f)
            val confidence = scores[maxIdx]
            val label = labels[maxIdx] ?: "idx_$maxIdx"

            Log.d("TFLite", "Result: $label (${"%.0f".format(confidence * 100)}%) " +
                    "| Top3: ${
                        scores.mapIndexed { i, s -> Pair(labels[i] ?: "$i", s) }
                            .sortedByDescending { it.second }
                            .take(3)
                            .map { "${it.first}=${"%.0f".format(it.second * 100)}%" }
                    }")

            Pair(label, confidence)

        } catch (e: Exception) {
            Log.e("TFLite", "classify() error: ${e.message}", e)
            Pair("?", 0f)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}