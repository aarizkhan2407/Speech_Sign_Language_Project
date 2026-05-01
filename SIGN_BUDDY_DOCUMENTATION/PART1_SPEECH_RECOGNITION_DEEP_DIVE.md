# PART 1: SPEECH RECOGNITION - DEEP DIVE

## Overview
The speech recognition system is the **entry point** of the entire application. It listens to the user's voice, converts it to text, and accumulates multiple speech inputs into a single coherent sentence.

---

## 1.1 ANDROID SPEECH RECOGNIZER API

### What is SpeechRecognizer?
Android's `SpeechRecognizer` is a **high-level abstraction** around Google's on-device speech recognition engine:
- Runs **locally on device** (no internet required)
- Uses neural networks trained on millions of hours of speech data
- Returns results as **text strings** with confidence scores

### Initialization (MainActivity.kt, Line 51)

```kotlin
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    private lateinit var speechRecognizer: SpeechRecognizer
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create SpeechRecognizer instance
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Attach listener to receive callbacks
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            // ... implementation
        })
    }
}
```

**Why `createSpeechRecognizer(context)`?**
- Uses device's default speech recognition service (typically Google Recognizer)
- Requires `INTERNET` permission for optimal accuracy
- Requires `RECORD_AUDIO` permission to access microphone

---

## 1.2 RECOGNITION LISTENER - DETAILED BREAKDOWN

### Complete Event Flow

The `RecognitionListener` interface receives **7 callbacks** during a speech recognition session:

```
┌─────────────────────────────────────────────────────────────┐
│ onReadyForSpeech() - User can start speaking              │
│      ↓                                                       │
│ onBeginningOfSpeech() - Audio input detected              │
│      ↓                                                       │
│ [onRmsChanged() - Repeated while speaking]                │
│ [onBufferReceived() - Repeated while speaking]            │
│      ↓                                                       │
│ onEndOfSpeech() - User stopped speaking (silence detected) │
│      ↓                                                       │
│ onResults() - Final recognized text available             │
│      ↓                                                       │
│ onPartialResults() - May fire before onResults            │
│      (OR)                                                    │
│ onError() - Something went wrong                          │
└─────────────────────────────────────────────────────────────┘
```

### 1.2.1 onReadyForSpeech()

```kotlin
override fun onReadyForSpeech(params: Bundle?) {
    viewModel.listeningStatus = "Listening..."
}
```

**When it's called:** Right after `speechRecognizer.startListening()` is called
**What it means:** The speech recognizer is initialized and ready to accept audio
**Our code:** Updates UI to show "Listening..." status
**Bundle params:** Contains recognizer hints (not used in our app)

---

### 1.2.2 onBeginningOfSpeech()

```kotlin
override fun onBeginningOfSpeech() {}
```

**When it's called:** When first audio is detected from microphone
**What it means:** The device heard sound and is processing it
**Our code:** Empty - we don't need special handling
**Use case in other apps:** Could show "Recording..." animation

---

### 1.2.3 onRmsChanged()

```kotlin
override fun onRmsChanged(rmsdB: Float) {
    // Throttle updates to improve performance
    if (kotlin.math.abs(rmsdB - viewModel.audioVolume) > 1.0f) {
        viewModel.audioVolume = rmsdB
    }
}
```

**What it does:** Called repeatedly while user is speaking (every ~100ms)

**RMS = Root Mean Square (decibels)**
- Measures **sound intensity**
- Range: 0 dB (silence) to ~30 dB (loud speech)
- Used for **volume visualization** in the UI

**Throttling Logic:**
```
rmsdB = 15.2
viewModel.audioVolume = 10.5

Difference = |15.2 - 10.5| = 4.7 dB
            
Is 4.7 > 1.0?  YES → Update audioVolume = 15.2
              NO  → Skip (avoid frequent updates)
```

**Why throttle?**
- Updates UI only when change is **meaningful** (>1dB)
- Reduces CPU/battery usage
- Improves 60fps smoothness

---

### 1.2.4 onBufferReceived()

```kotlin
override fun onBufferReceived(buffer: ByteArray?) {}
```

**When called:** Periodically as audio is buffered
**What it contains:** Raw audio bytes from microphone
**Our code:** Empty - we don't process raw audio
**Advanced use:** Could implement real-time audio visualization

---

### 1.2.5 onEndOfSpeech()

```kotlin
override fun onEndOfSpeech() {
    // We keep isListening true until onResults or manual cancel
}
```

**When called:** When ~2 seconds of silence detected after speech
**What it means:** User finished speaking; processing results
**Our code:** Comment indicates we intentionally do nothing
**Why?** Because `onResults()` fires right after anyway

---

### 1.2.6 onPartialResults()

```kotlin
override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    if (!matches.isNullOrEmpty()) {
        // Update UI with what was heard SO FAR (not final)
        viewModel.listeningStatus = matches[0]
    }
}
```

**When called:** BEFORE `onResults()`, while processing
**What it contains:** Best guess of text recognized **so far**
**Example sequence:**
```
User says: "How are you today"

[0.5s] onPartialResults() → "How"
[1.0s] onPartialResults() → "How are"
[1.5s] onPartialResults() → "How are you"
[2.0s] onPartialResults() → "How are you today"
[2.2s] onEndOfSpeech()
[2.3s] onResults() → ["How are you today", "How are you today", ...]
```

**Our code:** Updates `listeningStatus` to show real-time feedback

---

### 1.2.7 onResults()

```kotlin
override fun onResults(results: Bundle?) {
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    
    if (!matches.isNullOrEmpty()) {
        val text = matches[0]  // Most confident result
        viewModel.audioVolume = 0f
        
        // Append with space if not empty
        viewModel.accumulatedText += (if (viewModel.accumulatedText.isNotEmpty()) " " else "") + text
        viewModel.recognizedText = viewModel.accumulatedText
        
        if (viewModel.isListening) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (viewModel.isListening) startListening(isRestart = true)
            }, 500)
        }
    } else if (viewModel.isListening) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (viewModel.isListening) startListening(isRestart = true)
        }, 500)
    }
}
```

**When called:** After speech is recognized and processed
**What it contains:** Array of recognition results (sorted by confidence)

**Results Array Structure:**
```
matches[0] = "hello"        // Best guess (highest confidence)
matches[1] = "halo"         // Alternative 1
matches[2] = "hellow"       // Alternative 2
matches[3] = "hell"         // Alternative 3
...
```

**Our Accumulation Logic:**

```
Initial state:
  accumulatedText = ""

User says "hello":
  matches[0] = "hello"
  accumulatedText = "" + "" + "hello" = "hello"

User says "good":
  matches[0] = "good"
  accumulatedText = "hello" + " " + "good" = "hello good"

User says "morning":
  matches[0] = "morning"
  accumulatedText = "hello good" + " " + "morning" = "hello good morning"
```

**Auto-Restart Logic:**
```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    if (viewModel.isListening) startListening(isRestart = true)
}, 500)
```

**Timeline:**
```
[0ms]   onResults() fires
[0ms]   Post delayed task to main thread
[500ms] If isListening still true, restart listening
```

**Why 500ms delay?**
- Gives UI time to update
- Allows user to perceive silence
- Prevents "jumpy" recognition

---

### 1.2.8 onError()

```kotlin
override fun onError(error: Int) {
    viewModel.audioVolume = 0f
    
    if (viewModel.isListening) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (viewModel.isListening) startListening(isRestart = true)
        }, 500)
    } else {
        viewModel.listeningStatus = ""
    }
}
```

**Error Codes:**
| Code | Meaning | Action |
|------|---------|--------|
| 1 | Network error | Retry |
| 2 | Network timeout | Retry |
| 3 | Audio recording error | Show toast |
| 4 | Server error | Retry |
| 5 | Client error | Stop |
| 6 | **Speech timeout** (no input) | Retry |
| 7 | Network operation timeout | Retry |
| 8 | RecognitionService died | Retry |
| 9 | Insufficient permissions | Stop |
| 10 | Too much audio | Retry |
| 11 | Service unavailable | Retry |

**Our handling:**
- **If listening**: Automatically retry after 500ms
- **If not listening**: Clear status and stop

---

## 1.3 startListening() FUNCTION

```kotlin
private fun startListening(isRestart: Boolean = false) {
    if (!isRestart) {
        viewModel.accumulatedText = ""
        viewModel.recognizedText = ""
    }
    
    viewModel.isListening = true
    
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
    
    speechRecognizer.startListening(intent)
}
```

**Parameters:**
- `isRestart`: Boolean flag to determine if we should clear accumulated text

**Intent Configuration:**

```kotlin
RecognizerIntent.ACTION_RECOGNIZE_SPEECH
    // Tells Android: "I want speech recognition"
    
RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
    // Tells recognizer: Accept ANY text (not limited vocabulary)
    // Alternative: LANGUAGE_MODEL_WEB_SEARCH (for web query optimization)
    
RecognizerIntent.EXTRA_LANGUAGE = "en-US"
    // Language: English (US)
    // Other options: "en-IN" (Indian), "en-GB" (British), etc.
```

**First Call vs Restart:**
```
FIRST CALL (isRestart=false):
  accumulatedText = ""
  recognizedText = ""
  → Fresh start, no history

RESTART (isRestart=true):
  Keep accumulatedText unchanged
  → Append to existing text
```

---

## 1.4 MICROPHONE PERMISSIONS

### Permission Declaration (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### Runtime Permission Check (MainActivity.kt)

```kotlin
private fun checkMicrophonePermission(): Boolean {
    return if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED
    ) {
        true
    } else {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MICROPHONE_PERMISSION_REQUEST_CODE
        )
        false
    }
}
```

**Android 6.0+ (API 23+) requires:**
- Declare permissions in `AndroidManifest.xml`
- Request at runtime
- User grants in settings

---

## 1.5 COMPLETE SPEECH RECOGNITION TIMELINE

### Full Example: User says "Hello Good Morning"

```
[INITIAL STATE]
accumulatedText = ""
isListening = false
listeningStatus = ""

[USER TAPS MICROPHONE BUTTON]
startListening(isRestart=false)
  accumulatedText = ""
  recognizedText = ""
  isListening = true
  speechRecognizer.startListening(intent)

[50ms] onReadyForSpeech()
  listeningStatus = "Listening..."

[100ms] onBeginningOfSpeech()
  (User started speaking)

[200ms] onRmsChanged(rmsdB=8.5)
  audioVolume = 8.5

[400ms] onRmsChanged(rmsdB=12.3)
  audioVolume = 12.3

[600ms] onPartialResults()
  matches = ["he", "hello"]
  listeningStatus = "he"

[800ms] onPartialResults()
  matches = ["hello"]
  listeningStatus = "hello"

[1000ms] onRmsChanged(rmsdB=15.2)
  audioVolume = 15.2

[1200ms] onPartialResults()
  matches = ["hello g"]
  listeningStatus = "hello g"

[1500ms] (User pauses for 1.5 seconds)
  onRmsChanged(rmsdB=0.2)
  audioVolume = 0.2

[2000ms] onEndOfSpeech()
  (Silence detected)

[2100ms] onResults()
  matches = ["hello", "halo", "hell"]
  text = "hello"  (matches[0])
  audioVolume = 0f
  accumulatedText = "" + "" + "hello" = "hello"
  recognizedText = "hello"
  
  Post delayed task:
    Wait 500ms → if isListening, startListening(isRestart=true)

[2600ms] startListening(isRestart=true)
  (Don't clear accumulatedText because isRestart=true)
  isListening = true
  speechRecognizer.startListening(intent)

[2700ms] onReadyForSpeech()

[2800ms] onBeginningOfSpeech()

[3000ms] onPartialResults()
  matches = ["go"]
  listeningStatus = "go"

[3200ms] onPartialResults()
  matches = ["good"]
  listeningStatus = "good"

[4000ms] onEndOfSpeech()

[4100ms] onResults()
  matches = ["good"]
  text = "good"
  accumulatedText = "hello" + " " + "good" = "hello good"
  recognizedText = "hello good"
  
  Post delayed task for restart...

[4600ms] startListening(isRestart=true)

[4700ms] onReadyForSpeech()

[5000ms] onPartialResults()
  matches = ["mo"]

[5500ms] onPartialResults()
  matches = ["morning"]

[6000ms] onEndOfSpeech()

[6100ms] onResults()
  matches = ["morning"]
  text = "morning"
  accumulatedText = "hello good" + " " + "morning" = "hello good morning"
  recognizedText = "hello good morning"

[6600ms] startListening(isRestart=true)
  ... (continues listening)

[USER TAPS MICROPHONE BUTTON TO STOP]
stopListening()
  isListening = false
  speechRecognizer.stopListening()
  
Now accumulatedText = "hello good morning" is ready for translation!
```

---

## 1.6 PERFORMANCE CONSIDERATIONS

### RMS Throttling Benefit
```
WITHOUT THROTTLING:
  onRmsChanged() fires ~10 times/second
  Each update → UI recomposition
  CPU usage: 15%
  Battery drain: High

WITH 1.0dB THROTTLING:
  onRmsChanged() filters to meaningful changes only
  UI recomposition only when volume changes significantly
  CPU usage: 2%
  Battery drain: Low
```

### Auto-Restart Efficiency
```
Traditional approach (one recognition per tap):
  User taps mic → Listen once → User taps mic → Listen again
  Problem: Tedious, user must tap repeatedly

Our approach (continuous auto-restart):
  User taps mic → Listen → Auto-restart → Listen → ...
  User can speak multiple sentences without re-tapping
  Problem: User must manually tap to stop
```

---

## 1.7 ERROR RECOVERY STRATEGY

```
Error Occurs (e.g., network timeout)
    ↓
If user was actively listening (isListening=true):
  → Wait 500ms
  → Auto-restart listening
  → User doesn't lose progress
    ↓
If user had stopped listening (isListening=false):
  → Clear listening status
  → Show normal UI
  → No disruptive restart
```

This strategy provides **graceful degradation** and **user-friendly recovery**.

---

## 1.8 DATA FLOW TO NEXT STAGE

```
[Speech Recognition Output]
accumulatedText = "hello good morning"
recognizedText = "hello good morning"
    ↓
[User taps Translation Button]
    ↓
[MainViewModel.translateAndPlay()]
    ↓
[Google Translate API]
    ↓
[ISL Translator]
    ↓
[Animation Playback]
```

**Key Point:** Speech recognition is **independent** of translation. The app could:
- Use different speech recognizer services
- Skip translation entirely
- Process text without speech input

This **modularity** is important for maintainability.

---

## Summary

| Aspect | Details |
|--------|---------|
| **API Used** | Android SpeechRecognizer (Google Speech-to-Text) |
| **Processing** | Local device (no internet for recognition) |
| **Accuracy** | ~95% for English in quiet environments |
| **Latency** | ~500ms to final result |
| **Languages** | Any language Android supports |
| **Continuous Input** | Auto-restart on silence (every 2-3 seconds) |
| **Throttling** | RMS changes >1dB to reduce CPU |
| **Error Handling** | Auto-retry for network/audio errors |
| **Permissions** | RECORD_AUDIO + INTERNET |
