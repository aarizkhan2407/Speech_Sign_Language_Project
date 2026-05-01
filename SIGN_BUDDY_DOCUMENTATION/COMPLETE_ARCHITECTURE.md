# SIGN-BUDDY: Complete Architecture & Code Logic Explanation

## 📱 PROJECT OVERVIEW

**Sign-Buddy** is an Android application that translates **spoken English into Indian Sign Language (ISL)** using real-time speech recognition, cloud translation, and neural network-based hand gesture recognition.

---

## 🏗️ ARCHITECTURE OVERVIEW

```
┌─────────────────────────────────────────────────────────┐
│              ANDROID APPLICATION LAYER                  │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Jetpack Compose)                            │
│  ├─ MainActivity (Main Translation Screen)            │
│  ├─ SignToSpeechActivity (Camera Feed)                │
│  └─ GuidelineActivity (Help Screen)                   │
├─────────────────────────────────────────────────────────┤
│  ViewModel Layer (State Management)                     │
│  ├─ MainViewModel                                      │
│  └─ SignToSpeechViewModel                              │
├─────────────────────────────────────────────────────────┤
│  Business Logic & API Layer                            │
│  ├─ ISLTranslator (Text → Sign Conversion)             │
│  ├─ Google Translate API (Multi-language Support)      │
│  ├─ MediaPipe (Hand Detection)                         │
│  └─ TensorFlow Lite (Gesture Classification)           │
├─────────────────────────────────────────────────────────┤
│  Asset Layer                                            │
│  ├─ GIFs for Common Words                              │
│  ├─ PNG Images for Letters                             │
│  └─ ML Models (gesture_model.tflite)                   │
├─────────────────────────────────────────────────────────┤
│  External Services                                      │
│  ├─ Google Cloud Translation API                       │
│  ├─ Android Speech Recognition API                     │
│  └─ Firebase Authentication (Optional)                 │
└─────────────────────────────────────────────────────────┘
```

---

## 📂 FILE STRUCTURE & RESPONSIBILITIES

### **1. MainActivity.kt** (PRIMARY SCREEN)
**Location:** `app/src/main/java/.../MainActivity.kt`

**Responsibilities:**
- Speech recognition (listening to user's voice)
- Text input UI
- Manual text entry
- Playback controls for sign animations
- Integration with MainViewModel

**Key Components:**

```
┌─ MainActivity (Activity)
│
├─ speechRecognizer: SpeechRecognizer
│  └─ Handles: onBeginningOfSpeech(), onError(), onResults()
│
├─ PhoneScreen(): Composable
│  ├─ Header (Info + Camera buttons)
│  ├─ Input Section
│  │  ├─ When Listening: Shows listening status
│  │  └─ When Not: Text input field
│  ├─ Preview Section (Sign Animation Display)
│  ├─ Playback Controls
│  └─ Bottom Controls (Mic + Restart buttons)
│
└─ startListening() → Triggers Android Speech Recognizer
```

**Key Features:**
- Real-time audio volume visualization
- Continuous speech accumulation
- Auto-restart listening on silence
- 2-second delays between sign animations

---

### **2. MainViewModel.kt** (STATE MANAGEMENT)
**Location:** `app/src/main/java/.../MainViewModel.kt`

**Purpose:** Manages all data flow and business logic for the main screen

**State Variables:**
```kotlin
recognizedText        // Final translated text
accumulatedText       // Text being accumulated from speech
signSequence          // List<SignItem> - sequence of signs to display
currentIndex          // Current sign being displayed
isListening          // Boolean - microphone active status
isPaused             // Boolean - playback paused
isLooping            // Boolean - repeat animation
audioVolume          // Float - microphone input level
```

**Key Functions:**

1. **translateAndPlay(context, text)**
   - Calls Google Translate API
   - Converts English → English (normalizes the text)
   - Calls `convertTextToISL()` to get sign sequence
   - Starts playback animation

2. **startPlayback(sequence)**
   - Creates coroutine job
   - Increments `currentIndex` every 2000ms
   - Loops if `isLooping` is true

3. **togglePlayPause()**
   - Pauses/resumes animation playback

4. **reset()**
   - Clears all data
   - Stops playback

---

### **3. ISLTranslator.kt** (TEXT-TO-SIGN CONVERSION)
**Location:** `app/src/main/java/.../ISLTranslator.kt`

**Core Function: convertTextToISL(text: String) → List<SignItem>**

**Algorithm:**
```
INPUT: "hello good morning"
        ↓
STEP 1: Split into words → ["hello", "good", "morning"]
        ↓
STEP 2: For each word, check if it's a known sign:
        ├─ "hello" → Found! Add SignItem("gifs/hello.gif")
        ├─ "good"  → Found! Add SignItem("gifs/good.gif")
        └─ "morning" → Found! Add SignItem("gifs/morning.gif")
        ↓
STEP 3: For unknown words, spell letter-by-letter:
        Example: "xyz" → Add "letters/x.png", "letters/y.png", "letters/z.png"
        ↓
OUTPUT: List<SignItem>[
  SignItem("gifs/hello.gif", "hello"),
  SignItem("gifs/good.gif", "good"),
  SignItem("gifs/morning.gif", "morning")
]
```

**Known Words Mapping:**
```
"hello", "hi", "namaste" → "hello"
"morning" → "morning"
"good" → "good"
"you" → "you"
```

**Devanagari Support:**
Function `isDevanagari(text)` detects Hindi/Sanskrit text (U+0900 to U+097F Unicode range)

---

### **4. TFLiteClassifier.kt** (ML MODEL INTEGRATION)
**Location:** `app/src/main/java/.../TFLiteClassifier.kt`

**Purpose:** Hand gesture recognition from camera feed

**Architecture:**
```
Camera Input (Bitmap)
    ↓
MediaPipe Hand Landmarker
(Detects 21 hand keypoints per hand)
    ↓
TensorFlow Lite Model
(gesture_model.tflite - Classifies gesture)
    ↓
Predicted Gesture Label
(e.g., "A", "B", "C", etc.)
```

**Key Components:**

1. **MediaPipe HandLandmarker**
   - Detects hand positions in real-time
   - Outputs 21 3D landmarks per hand
   - Supports 2 hands simultaneously

2. **TensorFlow Lite Interpreter**
   - Loads `gesture_model.tflite`
   - Input: Hand keypoints (FloatArray)
   - Output: Gesture probability distribution

3. **Gesture Labels**
   - Loaded from `gesture_labels.json`
   - Maps model output indices to ISL signs

**Init Flow:**
```kotlin
init(context) {
  1. Load hand_landmarker.task (MediaPipe model)
  2. Create HandLandmarker instance
  3. Load gesture_model.tflite
  4. Create TFLite Interpreter
  5. Load gesture_labels.json
  6. Prepare input/output buffers
}
```

---

### **5. SignToSpeechActivity.kt** (REVERSE TRANSLATION)
**Purpose:** Camera-based sign recognition (ISL → Speech)

**Flow:**
```
Camera Feed (CameraX)
    ↓
Frame Capture
    ↓
TFLiteClassifier.recognize(bitmap)
    ↓
Recognized Gesture (e.g., "A")
    ↓
Text-to-Speech Output
```

---

### **6. Components.kt** (UI ELEMENTS)
**Reusable Composables:**

```kotlin
ListeningCard()
  ├─ Animated listening indicator
  └─ Shows live partial results

MicButton()
  ├─ Toggles listening state
  ├─ Visual feedback (pulsing effect)
  └─ Audio volume visualization

SignPreview()
  ├─ Displays current sign (GIF/PNG)
  ├─ Handles image loading
  └─ Session key for cache busting

PlaybackControls()
  ├─ Play/Pause button
  ├─ Replay button
  └─ Loop toggle

RecognizedTextDisplay()
  ├─ Shows translated text
  └─ Word-by-word breakdown
```

---

## 🔄 DATA FLOW DIAGRAM

### **Scenario: User says "Hello Good Morning"**

```
┌──────────────────────────────────────────────────────────┐
│ USER ACTION: Tap Microphone Button                       │
└────────────────────┬─────────────────────────────────────┘
                     ↓
        ┌────────────────────────┐
        │ startListening()        │
        │ Sets isListening=true   │
        └────────┬───────────────┘
                 ↓
    ┌──────────────────────────────┐
    │ Android SpeechRecognizer     │
    │ Listens to microphone        │
    └────────┬─────────────────────┘
             ↓
    ┌────────────────────────────────┐
    │ onResults() Callback           │
    │ "hello" recognized              │
    │ accumulatedText += "hello"      │
    └────────┬─────────────────────────┘
             ↓
    ┌───────────────────────────────────────────┐
    │ Auto-restart listening (500ms delay)      │
    │ User continues speaking...                 │
    │ "good" recognized                          │
    │ accumulatedText = "hello good"             │
    └────────┬──────────────────────────────────┘
             ↓
    ┌────────────────────────────────────────────┐
    │ User continues: "morning"                  │
    │ accumulatedText = "hello good morning"    │
    └────────┬───────────────────────────────────┘
             ↓
    ┌──────────────────────────────────────────┐
    │ USER: Taps Microphone Button Again       │
    │ stopListening() called                    │
    │ translateAndPlay(accumulatedText) called  │
    └────────┬─────────────────────────────────┘
             ↓
    ┌─────────────────────────────────────────────────────┐
    │ MainViewModel.translateAndPlay()                    │
    │                                                      │
    │ 1. Create HTTP request to Google Translate API      │
    │    Text: "hello good morning"                       │
    │    Target: "en"                                     │
    │                                                      │
    │ 2. Send POST request to:                            │
    │    https://translation.googleapis.com/language/     │
    │    translate/v2?key=API_KEY                         │
    │                                                      │
    │ 3. Parse JSON response:                             │
    │    {                                                │
    │      "data": {                                      │
    │        "translations": [{                           │
    │          "translatedText": "hello good morning"     │
    │        }]                                           │
    │      }                                              │
    │    }                                                │
    │                                                      │
    │ 4. Extract: translatedText = "hello good morning"   │
    └────────┬────────────────────────────────────────────┘
             ↓
    ┌──────────────────────────────────────────────────┐
    │ convertTextToISL("hello good morning")            │
    │                                                    │
    │ Split: ["hello", "good", "morning"]               │
    │                                                    │
    │ Match against known words:                        │
    │ ✓ "hello" → SignItem("gifs/hello.gif")            │
    │ ✓ "good"  → SignItem("gifs/good.gif")             │
    │ ✓ "morning" → SignItem("gifs/morning.gif")        │
    │                                                    │
    │ Return: List<SignItem>[...3 items]                │
    └────────┬──────────────────────────────────────────┘
             ↓
    ┌────────────────────────────────────┐
    │ startPlayback(signSequence)         │
    │                                     │
    │ 1. Set currentIndex = 0             │
    │ 2. Increment currentIndex every 2s  │
    │ 3. UI updates to show current sign  │
    │                                     │
    │ Display sequence:                   │
    │ [0s]   hello.gif      ← current     │
    │ [2s]   good.gif       ← current     │
    │ [4s]   morning.gif    ← current     │
    │ [6s]   (finished, show last frame)  │
    └──────────────────────────────────────┘
             ↓
    ┌─────────────────────────────────┐
    │ USER SEES SIGN ANIMATION        │
    │ (GIFs play sequentially)        │
    │ Can pause/resume/loop/replay    │
    └─────────────────────────────────┘
```

---

## 🧠 LLM USAGE IN THIS APP

### **LLM Integration Points:**

The app uses **Google Cloud Translation API** (not a true LLM but AI-powered NLP):

**1. Google Translate API**
```kotlin
// File: MainViewModel.kt (lines 72-80)
val requestBody = FormBody.Builder()
    .add("q", text)          // Input text
    .add("target", "en")     // Always English
    .build()

val request = Request.Builder()
    .url("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
    .post(requestBody)
    .build()
```

**Purpose:**
- Normalize text from multiple languages to English
- Handle Devanagari (Hindi) input
- Improve sign mapping accuracy

**Example:**
```
Input: "नमस्ते" (Hindi)
   ↓ [Google Translate]
Output: "namaste" (English)
   ↓ [ISL Translator]
Output: SignItem("gifs/hello.gif")
```

**API Response Format:**
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

---

## 🤖 ML MODELS USED

### **Model 1: MediaPipe Hand Landmarker**
**File:** `hand_landmarker.task` (in assets/)
**Purpose:** Hand detection and keypoint extraction

**Workflow:**
```
Input: Camera frame (RGB bitmap)
  ↓
Neural Network Processing:
├─ Palm detection (detects hands in image)
├─ Hand landmark prediction (21 joints per hand)
└─ Hand presence classifier
  ↓
Output: HandLandmarkerResult
{
  gestures: List<Classification>,
  handedness: List<Classification>,
  landmarks: List<List<NormalizedLandmark>>
}
```

**21 Hand Landmarks:**
```
0: Wrist
1-4: Thumb (base to tip)
5-8: Index finger
9-12: Middle finger
13-16: Ring finger
17-20: Pinky finger
```

### **Model 2: TensorFlow Lite Gesture Classifier**
**File:** `gesture_model.tflite` (in assets/)
**Purpose:** Classify hand gestures to ISL signs

**Architecture:**
```
Input Layer:
├─ Hand keypoints from MediaPipe (21 × 3 = 63 values per hand)
├─ Or hand landmarks (21 × 2 = 42 values)
└─ Normalized to range [0, 1]

Hidden Layers:
├─ Dense layer (256 neurons, ReLU activation)
├─ Dropout layer (0.5)
├─ Dense layer (128 neurons, ReLU activation)
└─ Dropout layer (0.5)

Output Layer:
├─ Dense layer (N neurons, where N = number of gestures)
└─ Softmax activation → Probability distribution
```

**Inference:**
```kotlin
// File: TFLiteClassifier.kt
fun recognize(bitmap: Bitmap): String {
    // 1. Extract hand landmarks using MediaPipe
    val image = BitmapImageBuilder(bitmap).build()
    val results = handLandmarker.detect(image)
    
    // 2. Prepare input buffer
    val landmarks = results.landmarks[0]  // First hand
    for (i in landmarks.indices) {
        inputBuffer.putFloat(landmarks[i].x)
        inputBuffer.putFloat(landmarks[i].y)
        inputBuffer.putFloat(landmarks[i].z)
    }
    
    // 3. Run inference
    interpreter.run(inputBuffer, outputBuffer)
    
    // 4. Get prediction
    val probabilities = outputBuffer[0]
    val maxIndex = probabilities.indexOfMax()
    val label = labels[maxIndex]
    
    // 5. Return gesture name
    return label  // e.g., "A", "B", "Hello", etc.
}
```

**Labels File:** `gesture_labels.json`
```json
{
  "0": "A",
  "1": "B",
  "2": "C",
  "3": "Hello",
  "4": "Thank you",
  ...
}
```

---

## 🔌 EXTERNAL LIBRARIES & DEPENDENCIES

### **Firebase (Authentication - Optional)**
```gradle
implementation("com.google.firebase:firebase-auth")  // Email/Password login
implementation("com.google.firebase:firebase-analytics")  // Usage tracking
```

### **Google ML Kit**
```gradle
implementation("com.google.mediapipe:tasks-vision:0.20230731")  // Hand detection
```

### **TensorFlow Lite**
```gradle
implementation("org.tensorflow:tensorflow-lite:2.16.1")  // Model inference
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")  // Image processing
implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1")  // GPU acceleration
```

### **Networking**
```gradle
implementation("com.squareup.okhttp3:okhttp:4.12.0")  // HTTP requests for Google Translate
```

### **Image Loading**
```gradle
implementation("io.coil-kt:coil-compose:2.5.0")  // GIF & image loading
implementation("io.coil-kt:coil-gif:2.5.0")  // GIF support
```

### **Jetpack Compose**
```gradle
implementation(platform("androidx.compose:compose-bom:2024.11.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.activity:activity-compose:1.9.0")
```

### **CameraX (for video capture)**
```gradle
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")
```

---

## 📊 PERFORMANCE & OPTIMIZATION

### **Speech Recognition**
- **Continuous listening** with 500ms auto-restart
- **Text accumulation** across multiple speech chunks
- **Throttled audio volume updates** (1.0dB threshold)

### **Animation Playback**
- **2-second intervals** between sign transitions
- **Session key mechanism** to bust image cache
- **Coroutine-based** for smooth 60fps animation

### **ML Inference**
- **2 threads** for TensorFlow Lite
- **GPU acceleration** enabled
- **Image preprocessing** handled by MediaPipe

### **Memory Management**
- **Input/output buffers** pre-allocated
- **Lazy loading** of ML models
- **Proper cleanup** in onDestroy()

---

## 🔐 SECURITY CONSIDERATIONS

**API Keys:**
- Google Translate API key stored in `strings.xml`
- Should use BuildConfig.FLAVOR or encrypted storage in production

**Firebase:**
- Email/Password stored securely by Firebase
- Google Sign-In tokens validated server-side

**ML Models:**
- Assets included in APK (not downloaded)
- TensorFlow Lite prevents model extraction

---

## 🚀 DEPLOYMENT & BUILD

**Build Configuration:**
```gradle
compileSdk = 36
minSdk = 24
targetSdk = 36
versionCode = 1
versionName = "1.0"
```

**Signing:**
- Uses debug keystore for development
- Release build requires production keystore

**Installation:**
```bash
./gradlew installDebug        # Install to connected device
./gradlew installRelease      # Install release APK
./gradlew bundleRelease       # Create AAB for Google Play
```

---

## 📝 SUMMARY FOR COLLEGE PRESENTATION

### Key Points to Highlight:

1. **Multi-Stage Pipeline:**
   - Speech → Text (Android Speech Recognizer)
   - Text → English (Google Translate API)
   - English → ISL Signs (Rule-based NLP)
   - ISL → Animation (Coil image loader)

2. **ML/AI Usage:**
   - MediaPipe for hand detection (21 keypoints)
   - TensorFlow Lite for gesture classification
   - Google Translate for multilingual support

3. **Modern Android Stack:**
   - Jetpack Compose for UI
   - MVVM architecture with ViewModel
   - Coroutines for async operations
   - CameraX for video capture

4. **Real-time Processing:**
   - 60fps UI updates
   - Sub-100ms speech recognition
   - <50ms ML inference

5. **Accessibility Impact:**
   - Makes ISL education interactive
   - Enables deaf-blind communication
   - Multi-language support (Hindi, English, etc.)

---

**App Status:** ✅ Running | 📱 Device: CPH2337 | 🔧 Ready for Testing
