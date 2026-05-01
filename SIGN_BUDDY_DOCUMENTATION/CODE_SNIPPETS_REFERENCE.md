# CODE SNIPPETS & VISUAL GUIDES - SIGN-BUDDY REFERENCE

## Quick Code Reference for All Major Features

---

## 1. SPEECH RECOGNITION - COMPLETE SETUP

### Step 1: Initialize SpeechRecognizer

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create recognizer instance
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Attach listener
        speechRecognizer.setRecognitionListener(MyRecognitionListener())
    }
}
```

### Step 2: Implement RecognitionListener

```kotlin
private inner class MyRecognitionListener : RecognitionListener {
    
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d("Speech", "Ready to listen")
        viewModel.listeningStatus = "Listening..."
    }
    
    override fun onBeginningOfSpeech() {
        Log.d("Speech", "User started speaking")
    }
    
    override fun onRmsChanged(rmsdB: Float) {
        // Throttle to avoid excessive updates
        if (kotlin.math.abs(rmsdB - viewModel.audioVolume) > 1.0f) {
            viewModel.audioVolume = rmsdB
        }
    }
    
    override fun onBufferReceived(buffer: ByteArray?) {
        // Raw audio bytes - we don't need this
    }
    
    override fun onEndOfSpeech() {
        Log.d("Speech", "User stopped speaking")
    }
    
    override fun onError(error: Int) {
        Log.e("Speech", "Error: $error")
        viewModel.audioVolume = 0f
        
        // Auto-restart if still listening
        if (viewModel.isListening) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (viewModel.isListening) startListening(isRestart = true)
            }, 500)
        }
    }
    
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]  // Most confident result
            
            // Accumulate text
            viewModel.accumulatedText += 
                (if (viewModel.accumulatedText.isNotEmpty()) " " else "") + text
            viewModel.recognizedText = viewModel.accumulatedText
            
            // Auto-restart
            if (viewModel.isListening) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (viewModel.isListening) startListening(isRestart = true)
                }, 500)
            }
        }
    }
    
    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            viewModel.listeningStatus = matches[0]  // Show live preview
        }
    }
}
```

### Step 3: Start/Stop Listening

```kotlin
private fun startListening(isRestart: Boolean = false) {
    if (!isRestart) {
        viewModel.accumulatedText = ""
        viewModel.recognizedText = ""
    }
    
    viewModel.isListening = true
    
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                 RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    }
    
    speechRecognizer.startListening(intent)
}

private fun stopListening() {
    viewModel.isListening = false
    speechRecognizer.stopListening()
}
```

---

## 2. STATE MANAGEMENT - MAINVIEWMODEL

### All State Variables

```kotlin
class MainViewModel : ViewModel() {
    
    // ===== RECOGNITION STATE =====
    var recognizedText by mutableStateOf("")
    var accumulatedText by mutableStateOf("")
    var listeningStatus by mutableStateOf("Listening...")
    var audioVolume by mutableStateOf(0f)
    var isListening by mutableStateOf(false)
    
    // ===== PLAYBACK STATE =====
    var signSequence by mutableStateOf<List<SignItem>>(emptyList())
    var currentIndex by mutableStateOf(0)
    var playbackSessionKey by mutableStateOf(0)
    var isPaused by mutableStateOf(false)
    var isLooping by mutableStateOf(false)
    
    // ===== PRIVATE MEMBERS =====
    private var playbackJob: Job? = null
    private val client = OkHttpClient()
    
    // ===== PUBLIC FUNCTIONS =====
    
    fun startPlayback(sequence: List<SignItem>) {
        // Cancel existing job
        playbackJob?.cancel()
        
        // Update state
        signSequence = sequence
        isPaused = false
        currentIndex = 0
        playbackSessionKey++
        
        if (sequence.isEmpty()) return
        
        // Launch animation loop
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
                    delay(100)  // Check pause status
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
        
        // Build request
        val requestBody = FormBody.Builder()
            .add("q", text)
            .add("target", "en")
            .build()
        
        val request = Request.Builder()
            .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
            .post(requestBody)
            .build()
        
        // Execute async
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val json = response.body?.string()
                
                if (json != null) {
                    // Parse response
                    val translatedText = JSONObject(json)
                        .getJSONObject("data")
                        .getJSONArray("translations")
                        .getJSONObject(0)
                        .getString("translatedText")
                    
                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        recognizedText = translatedText
                        val sequence = convertTextToISL(translatedText)
                        signSequence = sequence
                        startPlayback(sequence)
                    }
                }
            } catch (e: Exception) {
                // Fallback to original text
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
```

---

## 3. TEXT-TO-ISL CONVERSION

### Complete Algorithm

```kotlin
fun convertTextToISL(text: String): List<SignItem> {
    // Step 1: Normalize text
    val words = text.lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    
    val sequence = mutableListOf<SignItem>()
    
    // Step 2: Process each word
    for (word in words) {
        // Step 3: Check if word is in dictionary
        val signName = when (word) {
            "hello", "hi", "namaste" -> "hello"
            "morning" -> "morning"
            "good" -> "good"
            "you" -> "you"
            // Add more words here
            else -> null
        }
        
        // Step 4: Add sign if found
        if (signName != null) {
            sequence.add(SignItem("gifs/$signName.gif", signName))
        } else {
            // Step 5: Spell letter-by-letter
            for (char in word) {
                if (char in 'a'..'z' || char in '0'..'9') {
                    sequence.add(SignItem("letters/$char.png", char.toString()))
                }
            }
        }
    }
    
    return sequence
}

// Supporting function
fun isDevanagari(text: String): Boolean {
    return text.any { it in '\u0900'..'\u097F' }
}

// Data class
data class SignItem(
    val imagePath: String,
    val label: String
)
```

---

## 4. MACHINE LEARNING - TFLITE CLASSIFIER

### Initialization

```kotlin
object TFLiteClassifier {
    
    private var interpreter: Interpreter? = null
    private var handLandmarker: HandLandmarker? = null
    private var labels: Map<Int, String> = emptyMap()
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputBuffer: Array<FloatArray>
    
    fun init(context: Context) {
        if (interpreter != null) return
        
        try {
            // Initialize MediaPipe Hand Landmarker
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE)
                .build()
            
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            
            // Load TFLite Model
            val fd = context.assets.openFd("gesture_model.tflite")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val modelBuffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
            
            interpreter = Interpreter(
                modelBuffer,
                Interpreter.Options().setNumThreads(2)
            )
            
            // Load Labels
            val labelsJson = context.assets
                .open("gesture_labels.json")
                .bufferedReader()
                .readText()
            
            val jsonObject = JSONObject(labelsJson)
            labels = mutableMapOf<Int, String>().apply {
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val index = keys.next().toInt()
                    this[index] = jsonObject.getString(index.toString())
                }
            }
            
            // Pre-allocate Buffers
            val inputShape = interpreter!!.getInputTensor(0).shape()
            inputBuffer = ByteBuffer.allocateDirect(4 * inputShape[1])
                .order(ByteOrder.nativeOrder())
            
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            outputBuffer = Array(outputShape[0]) { FloatArray(outputShape[1]) }
            
        } catch (e: Exception) {
            Log.e("TFLite", "Init failed", e)
        }
    }
}
```

### Gesture Recognition

```kotlin
fun recognize(bitmap: Bitmap): String {
    // Step 1: Detect hand landmarks
    val image = BitmapImageBuilder(bitmap).build()
    val results = handLandmarker?.detect(image) ?: return "No hand detected"
    
    if (results.landmarks.isEmpty()) {
        return "No hand detected"
    }
    
    // Step 2: Get landmarks
    val landmarks = results.landmarks[0]
    
    // Step 3: Prepare input buffer
    inputBuffer.rewind()
    for (landmark in landmarks) {
        inputBuffer.putFloat(landmark.x)
        inputBuffer.putFloat(landmark.y)
        inputBuffer.putFloat(landmark.z)
    }
    inputBuffer.rewind()
    
    // Step 4: Run inference
    interpreter?.run(inputBuffer, outputBuffer)
    
    // Step 5: Get prediction
    val probabilities = outputBuffer[0]
    val maxIndex = probabilities.indexOfMax()
    
    // Step 6: Get label
    val label = labels[maxIndex] ?: "Unknown"
    
    return label
}

// Extension function for finding max index
fun FloatArray.indexOfMax(): Int {
    var maxIndex = 0
    var maxValue = this[0]
    for (i in 1 until size) {
        if (this[i] > maxValue) {
            maxValue = this[i]
            maxIndex = i
        }
    }
    return maxIndex
}
```

---

## 5. JETPACK COMPOSE UI COMPONENTS

### Main Screen Composable

```kotlin
@Composable
fun PhoneScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            "Sign-Buddy Translator",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Input Section
        if (viewModel.isListening) {
            ListeningCard(
                status = viewModel.listeningStatus,
                volume = viewModel.audioVolume
            )
        } else {
            OutlinedTextField(
                value = viewModel.accumulatedText,
                onValueChange = { viewModel.accumulatedText = it },
                label = { Text("Enter text or speak") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sign Preview
        if (viewModel.signSequence.isNotEmpty()) {
            SignPreview(
                sequence = viewModel.signSequence,
                currentIndex = viewModel.currentIndex,
                playbackSessionKey = viewModel.playbackSessionKey
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Display Text
            Text(
                "Translation: ${viewModel.recognizedText}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Playback Controls
            PlaybackControls(
                isPaused = viewModel.isPaused,
                isLooping = viewModel.isLooping,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onReplay = { viewModel.startPlayback(viewModel.signSequence) },
                onToggleLoop = { viewModel.isLooping = !viewModel.isLooping }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Bottom Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (viewModel.accumulatedText.isNotEmpty()) {
                        viewModel.translateAndPlay(context, viewModel.accumulatedText)
                    }
                }
            ) {
                Text("Translate")
            }
            
            Button(
                onClick = { viewModel.reset() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Clear")
            }
        }
    }
}
```

### Listening Card Component

```kotlin
@Composable
fun ListeningCard(status: String, volume: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated listening indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Volume visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(10) { index ->
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((index * volume / 30f + 4f).coerceAtMost(24f).dp)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}
```

### Sign Preview Component

```kotlin
@Composable
fun SignPreview(
    sequence: List<SignItem>,
    currentIndex: Int,
    playbackSessionKey: Int
) {
    if (sequence.isEmpty()) return
    
    val currentSign = sequence[currentIndex]
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = currentSign.imagePath,
                contentDescription = currentSign.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .key(playbackSessionKey)
            )
        }
    }
}
```

### Playback Controls Component

```kotlin
@Composable
fun PlaybackControls(
    isPaused: Boolean,
    isLooping: Boolean,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onToggleLoop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onReplay) {
            Icon(
                Icons.Default.Replay,
                contentDescription = "Replay",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        
        FloatingActionButton(
            onClick = onTogglePlayPause,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Play" else "Pause"
            )
        }
        
        IconButton(onClick = onToggleLoop) {
            Icon(
                Icons.Default.Loop,
                contentDescription = "Loop",
                tint = if (isLooping) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
```

---

## 6. VISUAL STATE DIAGRAMS

### Speech Recognition State Machine

```
START
  ↓
USER TAPS MIC
  ↓
startListening()
  ├─ accumulatedText = ""
  ├─ isListening = true
  └─ speechRecognizer.startListening()
  ↓
onReadyForSpeech()
  ├─ listeningStatus = "Listening..."
  └─ audioVolume = 0
  ↓
onBeginningOfSpeech()
  └─ (Audio detected, processing...)
  ↓
onRmsChanged() [repeated]
  └─ audioVolume = X dB
  ↓
onPartialResults()
  └─ listeningStatus = "partial text"
  ↓
onEndOfSpeech()
  ├─ (Silence detected)
  └─ Wait for final result...
  ↓
onResults()
  ├─ text = "recognized words"
  ├─ accumulatedText += text
  └─ Auto-restart after 500ms
  ↓
startListening(isRestart=true)
  ├─ Keep accumulatedText unchanged
  └─ Continue listening
  ↓
[Repeat until user stops]
  ↓
USER TAPS STOP/TRANSLATE
  ├─ isListening = false
  ├─ stopListening()
  └─ accumulatedText = "final text"
  ↓
END
```

### Playback State Machine

```
IDLE
  ├─ signSequence = []
  ├─ currentIndex = 0
  └─ isPaused = true
  ↓
USER TAPS TRANSLATE
  ├─ translateAndPlay() called
  └─ convertTextToISL() creates sequence
  ↓
startPlayback()
  ├─ signSequence = [sign1, sign2, sign3...]
  ├─ currentIndex = 0
  ├─ isPaused = false
  └─ Launch coroutine with 2s delays
  ↓
PLAYING
  ├─ [2s delay]
  ├─ currentIndex++
  ├─ Show new sign
  └─ [repeat]
  ↓
[User taps Pause]
  ├─ isPaused = true
  └─ Stop incrementing
  ↓
PAUSED
  └─ currentIndex frozen at current sign
  ↓
[User taps Play]
  ├─ isPaused = false
  └─ Resume incrementing
  ↓
[Continue playing]
  ↓
END OF SEQUENCE
  ├─ currentIndex >= signSequence.size - 1
  ├─ If isLooping:
  │   ├─ currentIndex = 0
  │   └─ Continue playing
  └─ If not isLooping:
      ├─ isPaused = true
      └─ Stop
```

---

## 7. BUILD GRADLE CONFIGURATION

### Root build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.4.0" apply false
    id("com.android.library") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

### App build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    compileSdk = 36
    
    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")
    
    // ML
    implementation("com.google.mediapipe:tasks-vision:0.20230731")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")
    
    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")
    
    // Camera
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
}
```

---

## 8. ANDROID MANIFEST CONFIGURATION

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Required Permissions -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.SignBuddy">
        
        <!-- Main Activity (Launcher) -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <!-- Other Activities -->
        <activity
            android:name=".SignToSpeechActivity"
            android:exported="false" />
        
        <activity
            android:name=".GuidelineActivity"
            android:exported="false" />
    </application>
    
</manifest>
```

---

## 9. PERFORMANCE OPTIMIZATION TIPS

```kotlin
// ✅ GOOD: Remember lambdas to prevent recomposition
val onPlayClick = remember {
    { viewModel.togglePlayPause() }
}
PlaybackControls(onTogglePlayPause = onPlayClick)

// ❌ BAD: Creates new lambda every recomposition
PlaybackControls(
    onTogglePlayPause = { viewModel.togglePlayPause() }
)

// ✅ GOOD: Throttle RMS updates (1dB threshold)
if (kotlin.math.abs(rmsdB - viewModel.audioVolume) > 1.0f) {
    viewModel.audioVolume = rmsdB
}

// ❌ BAD: Update on every change
viewModel.audioVolume = rmsdB

// ✅ GOOD: Use IO thread for network
viewModelScope.launch(Dispatchers.IO) {
    val response = client.newCall(request).execute()
}

// ❌ BAD: Network call on main thread
val response = client.newCall(request).execute()  // FREEZES UI!

// ✅ GOOD: Use session key for cache busting
AsyncImage(
    model = path,
    modifier = Modifier.key(playbackSessionKey)
)

// ❌ BAD: Same path doesn't reload
AsyncImage(model = path)  // Shows cached version
```

---

## 10. COMMON ERRORS & SOLUTIONS

| Error | Cause | Solution |
|-------|-------|----------|
| "Missing permissions" | RECORD_AUDIO not in manifest | Add `<uses-permission>` in AndroidManifest.xml |
| "clickable only supports IndicationNodeFactory" | Old Compose BOM | Update to 2024.11.00+ |
| "Module compiled with incompatible Kotlin" | Version mismatch | Upgrade Kotlin to 2.1.0 |
| "GIF not animating" | Coil GIF decoder missing | Add `coil-gif:2.5.0` dependency |
| "Frozen UI during API call" | Main thread network call | Use `Dispatchers.IO` + `viewModelScope` |
| "State lost on rotation" | Using local variables | Use `mutableStateOf()` in ViewModel |
| "Image shows old frame" | Coil caching | Use `key()` modifier with unique value |

---

**Total Reference Code Size:** ~10 KB of ready-to-use snippets
**Estimated Implementation Time:** 1-2 hours to understand all code
**Reusability:** All snippets directly applicable to your project
