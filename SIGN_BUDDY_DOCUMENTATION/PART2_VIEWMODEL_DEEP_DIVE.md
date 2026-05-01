# PART 2: STATE MANAGEMENT - MAINVIEWMODEL DEEP DIVE

## Overview
`MainViewModel` is the **heart** of the application. It manages:
- Speech recognition state
- Translation state
- Animation playback
- User interaction state

Using **MVVM (Model-View-ViewModel)** architecture, it keeps the UI layer **independent** from business logic.

---

## 2.1 UNDERSTANDING MVVM ARCHITECTURE

```
┌─────────────────┐
│   UI Layer      │
│  (Composables)  │
└────────┬────────┘
         │ observes
         ↓
┌─────────────────────────────────────┐
│   ViewModel (MainViewModel)         │
│   ├─ State (mutableStateOf)         │
│   ├─ Functions (startPlayback)      │
│   └─ Coroutines (viewModelScope)    │
└────────┬────────────────────────────┘
         │ manages
         ↓
┌─────────────────────────────────────┐
│   Business Logic & Services         │
│   ├─ Translation API calls          │
│   ├─ Data transformations           │
│   └─ Side effects (HTTP requests)   │
└─────────────────────────────────────┘
```

**Key Principle:** When ViewModel state changes → UI automatically recomposes

---

## 2.2 MAINVIEWMODEL CLASS STRUCTURE

```kotlin
class MainViewModel : ViewModel() {
    
    // ===== STATE VARIABLES (Observable) =====
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
    
    // ===== PRIVATE STATE (Not observable) =====
    private var playbackJob: Job? = null
    private val client = OkHttpClient()
    
    // ===== PUBLIC FUNCTIONS =====
    fun startPlayback(sequence: List<SignItem>) { }
    fun stopPlayback() { }
    fun translateAndPlay(context: Context, text: String) { }
    fun togglePlayPause() { }
    fun reset() { }
}
```

---

## 2.3 STATE VARIABLES - DETAILED BREAKDOWN

### 2.3.1 Recognition State

```kotlin
var recognizedText by mutableStateOf("")
var accumulatedText by mutableStateOf("")
var listeningStatus by mutableStateOf("Listening...")
var audioVolume by mutableStateOf(0f)
var isListening by mutableStateOf(false)
```

#### recognizedText
**Purpose:** Displays final translated text to user
**Updated by:** `onResults()` in MainActivity
**Example:**
```
User speaks: "hello good morning"
recognizedText = "hello good morning"
UI displays: "hello good morning"
```

#### accumulatedText
**Purpose:** Buffers multiple speech recognition results
**Updated by:** `onResults()` in MainActivity
**Accumulation example:**
```
Initial: accumulatedText = ""

After 1st recognition:
  accumulatedText = "hello"

After 2nd recognition:
  accumulatedText = "hello good"

After 3rd recognition:
  accumulatedText = "hello good morning"
```

#### listeningStatus
**Purpose:** Shows real-time feedback while listening
**Updated by:** `onPartialResults()` and `onReadyForSpeech()`
**Example flow:**
```
[0.5s] listeningStatus = "Listening..."  (onReadyForSpeech)
[1.0s] listeningStatus = "He"             (onPartialResults)
[1.5s] listeningStatus = "Hello"          (onPartialResults)
[2.0s] listeningStatus = "Hello go"       (onPartialResults)
[2.5s] listeningStatus = "Hello good"     (onPartialResults)
```

#### audioVolume
**Purpose:** For volume visualization (waveform/equalizer UI)
**Updated by:** `onRmsChanged()` with throttling
**Range:** 0-30 dB
**Visualization example:**
```
audioVolume = 5dB  → ▁ (small bar)
audioVolume = 15dB → ███ (medium bar)
audioVolume = 25dB → █████ (tall bar)
```

#### isListening
**Purpose:** Flag indicating if microphone is active
**Values:** true/false
**Controls:**
- Visibility of "Listening..." UI
- Auto-restart behavior
- Microphone button appearance

---

### 2.3.2 Playback State

```kotlin
var signSequence by mutableStateOf<List<SignItem>>(emptyList())
var currentIndex by mutableStateOf(0)
var playbackSessionKey by mutableStateOf(0)
var isPaused by mutableStateOf(false)
var isLooping by mutableStateOf(false)
```

#### signSequence
**Purpose:** List of signs to display
**Type:** `List<SignItem>`
**Example:**
```kotlin
signSequence = listOf(
    SignItem("gifs/hello.gif", "hello"),
    SignItem("gifs/good.gif", "good"),
    SignItem("gifs/morning.gif", "morning")
)
```

#### currentIndex
**Purpose:** Pointer to current sign in sequence
**Range:** 0 to (signSequence.size - 1)
**Timeline:**
```
startPlayback() called
  currentIndex = 0     → Display signSequence[0]
  [2s delay]
  currentIndex = 1     → Display signSequence[1]
  [2s delay]
  currentIndex = 2     → Display signSequence[2]
  [2s delay]
  currentIndex = 3     → (End of sequence, stop)
```

#### playbackSessionKey
**Purpose:** Force image reload when animation restarts
**Why needed?** Image caching issue:
```
Scenario 1 (WITHOUT session key):
  Display hello.gif (cached)
  Stop playback
  Start playback again
  hello.gif loads from cache → shows same image instantly
  Problem: Can't tell if animation restarted or continued

Scenario 2 (WITH session key):
  playbackSessionKey = 0
  Display hello.gif with key=0 (cached)
  Stop playback
  Start playback
  playbackSessionKey = 1 (incremented)
  AsyncImage sees different key → reloads image from source
  Animation visibly restarts
```

**Implementation in UI:**
```kotlin
AsyncImage(
    model = currentSign.imagePath,
    contentDescription = null,
    modifier = Modifier.key(playbackSessionKey)  // Force reload
)
```

#### isPaused
**Purpose:** Pause/resume playback state
**Timeline:**
```
startPlayback()
  isPaused = false
  
  [Playback running...]
  
  togglePlayPause()
  isPaused = true
  
  [Playback frozen at currentIndex]
  
  togglePlayPause()
  isPaused = false
  
  [Playback resumes from same position]
```

**Logic in playback job:**
```kotlin
playbackJob = viewModelScope.launch {
    while (isActive) {
        if (!isPaused) {  // Only advance when NOT paused
            if (currentIndex < sequence.size - 1) {
                delay(2000)
                currentIndex++
            } else {
                // Handle end of sequence
            }
        } else {
            delay(100)  // Check pause status frequently
        }
    }
}
```

#### isLooping
**Purpose:** Auto-restart animation when finished
**Timeline (with looping):**
```
startPlayback()
isLooping = true

[0s]   currentIndex = 0 → hello.gif
[2s]   currentIndex = 1 → good.gif
[4s]   currentIndex = 2 → morning.gif
[6s]   (end reached)
       → Reset: currentIndex = 0
       → playbackSessionKey++
[6s]   currentIndex = 0 → hello.gif (again)
[8s]   currentIndex = 1 → good.gif (again)
...    (continues indefinitely)
```

---

## 2.4 STARTPLAYBACK() - DETAILED IMPLEMENTATION

```kotlin
fun startPlayback(sequence: List<SignItem>) {
    // Step 1: Cancel any existing playback
    playbackJob?.cancel()
    
    // Step 2: Update sequence reference
    signSequence = sequence
    
    // Step 3: Reset playback state
    isPaused = false
    currentIndex = 0
    playbackSessionKey++  // Force image reload
    
    // Step 4: Exit early if sequence is empty
    if (sequence.isEmpty()) return

    // Step 5: Launch coroutine to handle timing
    playbackJob = viewModelScope.launch {
        while (isActive) {
            if (!isPaused) {
                // Check if we haven't reached the end
                if (currentIndex < sequence.size - 1) {
                    delay(2000)  // Wait 2 seconds
                    currentIndex++  // Move to next sign
                } else {
                    // We've reached the last item
                    if (isLooping) {
                        delay(2000)
                        currentIndex = 0
                        playbackSessionKey++
                    } else {
                        // Stop and exit
                        isPaused = true
                        break
                    }
                }
            } else {
                // If paused, check frequently
                delay(100)
            }
        }
    }
}
```

### Detailed Flow Diagram

```
startPlayback([hello, good, morning])
    ↓
1. Cancel existing job (if any)
    ↓
2. signSequence = [hello, good, morning]
    ↓
3. isPaused = false
   currentIndex = 0
   playbackSessionKey++ (e.g., 0 → 1)
    ↓
4. sequence.isEmpty()? NO → Continue
    ↓
5. Launch viewModelScope.launch:
    
    [Loop iteration 1]
    isPaused = false? YES
      currentIndex (0) < size (3)? YES
        delay(2000)
        currentIndex = 1
    
    [Loop iteration 2]
    isPaused = false? YES
      currentIndex (1) < size (3)? YES
        delay(2000)
        currentIndex = 2
    
    [Loop iteration 3]
    isPaused = false? YES
      currentIndex (2) < size (3)? NO
        isLooping? 
          YES → delay(2000), currentIndex = 0, playbackSessionKey++
          NO  → isPaused = true, break
```

### Key Design Decisions

**2-Second Delay:**
```
Why 2 seconds between signs?

Too fast (500ms):
  User can't distinguish individual signs
  Looks like blur of images
  Cognitive overload

2 seconds:
  Time for user to recognize sign
  Natural pace for instruction
  Aligns with human processing speed

Too slow (5+ seconds):
  Frustrating for user
  Too much idle time
  Unnatural rhythm
```

**Using viewModelScope:**
```
why not GlobalScope.launch?

GlobalScope:
  ✗ Continues after ViewModel destroyed
  ✗ Causes memory leaks
  ✗ Can cause crashes

viewModelScope:
  ✓ Automatically cancelled on ViewModel cleanup
  ✓ Tied to Activity/Fragment lifecycle
  ✓ Memory safe
  ✓ Best practice
```

---

## 2.5 TRANSLATEANDPLAY() - GOOGLE TRANSLATE INTEGRATION

```kotlin
fun translateAndPlay(context: Context, text: String) {
    val apiKey = context.getString(R.string.translate_api_key)
    
    // Step 1: Build request body
    val requestBody = FormBody.Builder()
        .add("q", text)           // Query text
        .add("target", "en")       // Always translate to English
        .build()

    // Step 2: Build HTTP request
    val request = Request.Builder()
        .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
        .post(requestBody)
        .build()

    // Step 3: Execute async on IO thread
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Step 4: Execute request
            val response = client.newCall(request).execute()
            val json = response.body?.string()

            if (json != null) {
                // Step 5: Parse JSON response
                val translatedText = JSONObject(json)
                    .getJSONObject("data")
                    .getJSONArray("translations")
                    .getJSONObject(0)
                    .getString("translatedText")

                // Step 6: Switch back to Main thread
                withContext(Dispatchers.Main) {
                    // Step 7: Update state
                    recognizedText = translatedText
                    
                    // Step 8: Convert to ISL signs
                    val sequence = convertTextToISL(translatedText)
                    signSequence = sequence
                    
                    // Step 9: Start playback
                    startPlayback(sequence)
                }
            }
        } catch (e: Exception) {
            // Step 10: Handle error - use original text
            withContext(Dispatchers.Main) {
                recognizedText = text
                val sequence = convertTextToISL(text)
                signSequence = sequence
                startPlayback(sequence)
            }
        }
    }
}
```

### 2.5.1 API Request Details

**Endpoint:** 
```
https://translation.googleapis.com/language/translate/v2?key=YOUR_API_KEY
```

**Request Type:** POST (HTTP POST method)

**Request Body (Form-encoded):**
```
q=hello good morning
target=en
```

**API Key:** Stored in `res/values/strings.xml`
```xml
<string name="translate_api_key">YOUR_KEY_HERE</string>
```

### 2.5.2 Response Parsing

**Example Response:**
```json
{
  "data": {
    "translations": [
      {
        "translatedText": "hello good morning",
        "detectedSourceLanguage": "en"
      }
    ]
  }
}
```

**Parsing Logic:**
```kotlin
JSONObject(json)                              // Root object
  .getJSONObject("data")                      // "data" field
  .getJSONArray("translations")               // "translations" array
  .getJSONObject(0)                           // First translation
  .getString("translatedText")                // Extract text
```

**Why translate to English?**
```
Scenario 1: User speaks Hindi
  Input: "नमस्ते" (Devanagari)
  → Google Translate: "नमस्ते" → "namaste" (English)
  → ISL Translator: "namaste" → SignItem(hello.gif)

Scenario 2: User speaks English
  Input: "hello"
  → Google Translate: "hello" → "hello" (same)
  → ISL Translator: "hello" → SignItem(hello.gif)
```

### 2.5.3 Threading & Concurrency

**The Problem:**
```
If we made HTTP request on Main thread:
  [HTTP request starts]
  [5000ms network latency]
  UI FROZEN for 5 seconds ← Bad UX!
  [Response received]
  UI unfrozen
```

**Our Solution:**
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    // Step 1: Execute on IO thread (not UI)
    val response = client.newCall(request).execute()  // Network call
    
    // Step 2: Switch back to Main thread for UI updates
    withContext(Dispatchers.Main) {
        recognizedText = translatedText  // Safe UI update
        startPlayback(sequence)          // Safe UI update
    }
}
```

**Timeline:**
```
[Main thread]
translateAndPlay() called
  ↓
launch(Dispatchers.IO)
  ↓
[Switch to IO thread]
  Network request → 5000ms
  Response parsing → 50ms
  ↓
[Switch back to Main thread]
  recognizedText = "..."
  startPlayback()
  ↓
[Compose recomposes]
  UI updates with new signs
```

**Key Benefits:**
- UI stays responsive during network call
- User can still tap buttons
- App doesn't freeze

### 2.5.4 Error Handling

**Network Error Scenario:**
```kotlin
try {
    val response = client.newCall(request).execute()
    // ... parsing logic
} catch (e: Exception) {
    // Exception caught here (network timeout, parse error, etc.)
    
    withContext(Dispatchers.Main) {
        // Fallback: Use original text
        recognizedText = text
        val sequence = convertTextToISL(text)
        signSequence = sequence
        startPlayback(sequence)
    }
}
```

**Error Types Handled:**
| Error | Cause | Response |
|-------|-------|----------|
| Network timeout | No internet | Use original text |
| Parse exception | Malformed JSON | Use original text |
| HTTP 401 | Invalid API key | Use original text |
| HTTP 403 | Quota exceeded | Use original text |
| Other exception | Unknown | Use original text |

**Why fallback to original text?**
```
Better UX than failing silently:
  User says: "hello" → Translation fails → Can still translate to ISL
  vs.
  User says: "hello" → Translation fails → No signs shown (bad)
```

---

## 2.6 TOGGLEPLAYPAUSE()

```kotlin
fun togglePlayPause() {
    if (currentIndex >= signSequence.size - 1 && isPaused) {
        // If at end of sequence and paused, restart from beginning
        startPlayback(signSequence)
    } else {
        // Otherwise, just toggle pause state
        isPaused = !isPaused
    }
}
```

**State Transitions:**

```
Scenario 1: Playback in progress
  currentIndex = 0 (showing hello)
  isPaused = false
  
  User taps Play/Pause button:
    togglePlayPause()
    currentIndex (0) >= size-1 (2)? NO
    → isPaused = !false = true
    → Animation pauses at hello

Scenario 2: Playback paused mid-sequence
  currentIndex = 1 (showing good)
  isPaused = true
  
  User taps Play/Pause button:
    togglePlayPause()
    currentIndex (1) >= size-1 (2)? NO
    → isPaused = !true = false
    → Animation resumes from good

Scenario 3: Animation finished, user taps Play/Pause
  currentIndex = 2 (showing morning - last)
  isPaused = true
  isLooping = false
  
  User taps Play/Pause button:
    togglePlayPause()
    currentIndex (2) >= size-1 (2)? YES
    isPaused == true? YES
    → startPlayback(signSequence)
    → Restart from beginning
    → currentIndex = 0
```

**Key Logic:** 
The condition `currentIndex >= signSequence.size - 1 && isPaused` handles the special case where playback finished and user wants to replay.

---

## 2.7 STOPPLAYBACK()

```kotlin
fun stopPlayback() {
    playbackJob?.cancel()           // Stop animation loop
    signSequence = emptyList()      // Clear signs
    isPaused = false
    isLooping = false
    // Don't reset currentIndex in case user wants to resume
}
```

**Usage:** When user navigates away or clears input

---

## 2.8 RESET()

```kotlin
fun reset() {
    recognizedText = ""
    accumulatedText = ""
    signSequence = emptyList()
    isPaused = false
    isLooping = false
    playbackSessionKey = 0
    playbackJob?.cancel()
}
```

**Usage:** When user taps "Clear" or "Reset" button

---

## 2.9 LIFECYCLE & CLEANUP

### ViewModel Scope

```kotlin
class MainViewModel : ViewModel() {
    private var playbackJob: Job? = null
    
    // Resources cleaned up automatically when ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()  // Explicit cleanup
        // OkHttpClient reuses thread pool - no explicit cleanup needed
    }
}
```

**Android Lifecycle:**
```
Activity Created
  ↓
ViewModels created (from viewModels() delegate)
  ↓
Activity in foreground
  ↓
Activity destroyed
  ↓
ViewModel.onCleared() called
  ↓
All coroutines in viewModelScope cancelled
  ↓
Memory freed
```

---

## 2.10 STATE DIAGRAM

```
┌─────────────────────────────────────────────────────┐
│            MAIN VIEWMODEL STATE MACHINE            │
└─────────────────────────────────────────────────────┘

                    [IDLE STATE]
              accumulatedText = ""
                 signSequence = []
                 currentIndex = 0
                  isPaused = true
                        ↓
              (User taps microphone)
                        ↓
                   [LISTENING]
                isListening = true
        listeningStatus = "Listening..."
                  audioVolume = Xdb
                        ↓
        (Speech recognition partial results)
                        ↓
              [ACCUMULATING TEXT]
            accumulatedText = "hello"
            recognizedText = "hello"
                        ↓
        (User finishes speaking, taps translate)
                        ↓
              [TRANSLATING VIA API]
           (Making HTTP request to Google)
                        ↓
              [CONVERTING TO ISL]
        signSequence = [hello.gif, good.gif, ...]
                        ↓
              [PLAYBACK RUNNING]
          currentIndex increments every 2 seconds
              isPaused = false
                        ↓
         (User taps Pause button)
                        ↓
              [PLAYBACK PAUSED]
              isPaused = true
          currentIndex frozen
                        ↓
         (User taps Play button)
                        ↓
    [PLAYBACK RUNNING] (from same position)
                        ↓
          (Sequence finishes)
                        ↓
    [PLAYBACK FINISHED] (isPaused = true, currentIndex at end)
                        ↓
    (User taps Reset button)
                        ↓
                   [IDLE STATE]
```

---

## 2.11 COMPARISON: TRADITIONAL VS MVVM

### Traditional Approach (Without ViewModel)

```kotlin
class MainActivity : ComponentActivity() {
    var recognizedText = ""
    var currentIndex = 0
    var signSequence = listOf<SignItem>()
    
    fun onResults(text: String) {
        recognizedText = text
        // UI update code mixed here
    }
}
```

**Problems:**
- ✗ State lost when Activity recreated (screen rotation)
- ✗ Business logic mixed with UI
- ✗ Hard to test
- ✗ Difficult to reuse logic in other screens

### MVVM Approach (Our Code)

```kotlin
class MainViewModel : ViewModel() {
    var recognizedText by mutableStateOf("")
    var signSequence by mutableStateOf(emptyList())
    
    fun translateAndPlay(context: Context, text: String) { }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Just observe state and render UI
    }
}
```

**Benefits:**
- ✓ State survives Activity recreation
- ✓ Business logic isolated in ViewModel
- ✓ Easy to test (inject mock ViewModel)
- ✓ Reusable across multiple screens
- ✓ Clear separation of concerns

---

## Summary Table

| Aspect | Details |
|--------|---------|
| **Pattern** | MVVM (Model-View-ViewModel) |
| **State Management** | Kotlin Compose mutableStateOf() |
| **Concurrency** | Coroutines with viewModelScope |
| **Threading** | IO for network, Main for UI |
| **Lifecycle** | Tied to ViewModel (survives config changes) |
| **Error Handling** | Graceful fallback to original text |
| **Performance** | Async operations prevent UI freeze |
| **Testing** | Easy unit testing without Android context |
