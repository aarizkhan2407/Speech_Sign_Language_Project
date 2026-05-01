# PART 4: UI & ARCHITECTURE - JETPACK COMPOSE DEEP DIVE

## Overview
This section covers:
1. Jetpack Compose fundamentals
2. UI component architecture
3. State reactivity & recomposition
4. Image loading & caching
5. Material Design 3 theming

---

## 4.1 JETPACK COMPOSE FUNDAMENTALS

### What is Jetpack Compose?

**Traditional Android UI (XML-based):**
```xml
<!-- activity_main.xml -->
<LinearLayout>
  <Button android:id="@+id/myButton" android:text="Click me" />
  <TextView android:id="@+id/myText" android:text="Hello" />
</LinearLayout>

<!-- MainActivity.kt -->
val button = findViewById<Button>(R.id.myButton)
button.setOnClickListener {
    val textView = findViewById<TextView>(R.id.myText)
    textView.text = "Clicked!"
}
```

**Jetpack Compose (Declarative):**
```kotlin
@Composable
fun MyScreen() {
    var clicked by remember { mutableStateOf(false) }
    
    Column {
        Button(onClick = { clicked = !clicked }) {
            Text("Click me")
        }
        if (clicked) {
            Text("Clicked!")
        }
    }
}
```

**Key Differences:**
| Aspect | XML | Compose |
|--------|-----|---------|
| **Paradigm** | Imperative | Declarative |
| **State** | Manual findViewById | Automatic via mutableStateOf |
| **Reactivity** | Manual update | Auto recomposition |
| **Learning** | Complex lifecycle | React-like model |
| **Performance** | Good | Excellent with skipIf optimization |

### Declarative UI Philosophy

```
STATE CHANGES
    ↓
describe(state) → UI hierarchy
    ↓
Render differences
    ↓
Update only changed parts
```

**Example:**
```kotlin
var count by mutableStateOf(0)

@Composable
fun Counter() {
    Column {
        Text("Count: $count")  // Reactive - updates when count changes
        Button(onClick = { count++ }) {
            Text("Increment")
        }
    }
}

// Timeline:
count = 0 → Compose draws Text("Count: 0")
count = 1 → Compose redraws Text("Count: 1")
count = 2 → Compose redraws Text("Count: 2")
```

---

## 4.2 COMPOSABLE FUNCTIONS

### What is a Composable?

A `@Composable` function:
- Describes UI as function result
- Can call other Composables
- **Cannot** call after return statement
- Executed multiple times (during recomposition)

```kotlin
@Composable
fun PlaybackControls(
    isPaused: Boolean,
    isLooping: Boolean,
    onTogglePlayPause: () -> Unit,
    onReplay: () -> Unit,
    onToggleLoop: () -> Unit
) {
    // Function body executed every recomposition
    Row(
        modifier = Modifier
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Buttons...
    }
}
```

### Composable Parameters

```kotlin
@Composable
fun MyComponent(
    // Parameters: passed from parent, trigger recomposition if changed
    isPaused: Boolean,      // Value type
    onToggle: () -> Unit,   // Lambda callback
    modifier: Modifier = Modifier  // Optional, defaults to empty
)
```

**Key Rule:** If a parameter changes → Composable recomposes

```
Parent state: var isPaused = true

MyComponent(isPaused = isPaused)
    ↓
Recompose triggered!
    ↓
@Composable fun MyComponent(isPaused: Boolean) {
    if (isPaused) {
        Text("Paused")  // Rendered
    } else {
        Text("Playing")
    }
}

Later: isPaused = false
    ↓
Recompose triggered!
    ↓
Text("Playing")  // Now rendered instead
```

---

## 4.3 STATE & REACTIVITY

### remember { mutableStateOf() }

**Problem:** Without state, variable changes don't trigger UI updates

```kotlin
var count = 0  // ❌ Not observable

@Composable
fun Counter() {
    Button(onClick = { count++ }) {  // count increases
        Text(count.toString())       // But UI doesn't update!
    }
}
```

**Solution:** Wrap in mutableStateOf()

```kotlin
@Composable
fun Counter() {
    var count by mutableStateOf(0)  // ✅ Observable
    
    Button(onClick = { count++ }) {  // count increases
        Text(count.toString())       // UI updates automatically!
    }
}
```

**What `remember` does:**

```
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    //             ^^^^^^^
    //             Preserves state across recompositions
}

Timeline:
[Initial composition]
  remember { mutableStateOf(0) }
  ↓ Creates MutableState(0)
  ↓ Stores in Compose state cache
  ↓ count = 0

[User clicks button]
  count++ → 1
  ↓
[Recomposition triggered]
  remember { mutableStateOf(0) }
  ↓ Returns SAME MutableState from cache
  ↓ count is still the same object with new value
  ↓ count = 1

Without remember:
[User clicks button]
  count++ → 1
  ↓
[Recomposition triggered]
  mutableStateOf(0)
  ↓ Creates NEW MutableState(0)
  ↓ count = 0  (RESET!)
```

### Derived State

```kotlin
var signSequence by mutableStateOf(listOf<SignItem>())
var currentIndex by mutableStateOf(0)

// Derived state (computed from other state)
val currentSign: SignItem? 
    get() = signSequence.getOrNull(currentIndex)

@Composable
fun DisplaySign() {
    val sign = currentSign  // Reads both signSequence and currentIndex
    
    if (sign != null) {
        // Display sign
    }
}

When either signSequence or currentIndex changes:
  → currentSign recomputes
  → UI updates
```

---

## 4.4 MODIFIERS - STYLING & LAYOUT

### Modifier Chain

Modifiers compose functionally from left-to-right:

```kotlin
Button(
    modifier = Modifier
        .padding(8.dp)                              // Apply padding first
        .background(Color.Blue, RoundedCornerShape(12.dp))  // Then color
        .size(200.dp)                               // Then size
        .clickable { }                              // Then interaction
)
```

**Visual order** (how to think about it):
```
1. Size (200×200)
2. Apply blue background with rounded corners
3. Apply padding (8dp around edges)
4. Enable clicking

Result: Button with blue background, rounded corners, padded, clickable
```

### Common Modifiers

```kotlin
// Size
.size(width = 100.dp, height = 50.dp)
.fillMaxWidth()
.fillMaxHeight()
.fillMaxSize()

// Spacing
.padding(all = 8.dp)
.padding(horizontal = 16.dp, vertical = 8.dp)

// Styling
.background(Color.Blue)
.background(Color.Gray, RoundedCornerShape(8.dp))

// Layout
.align(Alignment.CenterHorizontally)
.fillMaxWidth()

// Interaction
.clickable { println("Clicked!") }
.hover { println("Hovered") }

// Graphics
.clip(RoundedCornerShape(8.dp))
.alpha(0.5f)
.rotation(45f)
```

---

## 4.5 LAYOUT COMPOSABLES

### Row (Horizontal Layout)

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Button(onClick = {}) { Text("Button 1") }
    Button(onClick = {}) { Text("Button 2") }
    Button(onClick = {}) { Text("Button 3") }
}
```

**Visual:**
```
┌─ Row ─────────────────────────────────────┐
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Button 1 │  │ Button 2 │  │ Button 3 │ │
│  └──────────┘  └──────────┘  └──────────┘ │
└────────────────────────────────────────────┘
  16dp spacing between items, vertically centered
```

### Column (Vertical Layout)

```kotlin
Column(
    modifier = Modifier.fillMaxHeight(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Text("Title")
    Text("Subtitle")
    Button(onClick = {}) { Text("Action") }
}
```

**Visual:**
```
┌─ Column ──────────┐
│    Title          │
│   8dp space       │
│   Subtitle        │
│   8dp space       │
│ ┌──────────────┐  │
│ │   Action     │  │
│ └──────────────┘  │
└───────────────────┘
```

### Box (Overlay Layout)

```kotlin
Box(modifier = Modifier.size(200.dp)) {
    Image(...)  // Behind
    Text("Label")  // On top
    Icon(...)  // On top of text
}
```

**Visual:**
```
┌─ Box ─────────────────┐
│  ┌────────────────┐   │
│  │  Image         │   │
│  │  (background)  │   │
│  └────────────────┘   │
│     "Label"           │  ← On top
│       [Icon]          │  ← On top
└───────────────────────┘
All stacked on top of each other
```

---

## 4.6 ASYNCIMAGE - GIF & IMAGE LOADING

### Coil Image Library

```gradle
implementation("io.coil-kt:coil-compose:2.5.0")
implementation("io.coil-kt:coil-gif:2.5.0")
```

### AsyncImage Implementation

```kotlin
@Composable
fun SignPreview(
    sequence: List<SignItem>,
    currentIndex: Int,
    isPaused: Boolean,
    playbackSessionKey: Int
) {
    if (sequence.isEmpty()) {
        Text("No signs to display")
        return
    }

    val currentSign = sequence[currentIndex]
    
    // Custom ImageLoader with GIF support
    val imageLoader = remember {
        ImageLoader.Builder(LocalContext.current)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    AsyncImage(
        model = currentSign.imagePath,
        contentDescription = currentSign.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
            .key(playbackSessionKey)  // Force reload on key change
    )
}
```

### Image Loading Pipeline

```
User provides: imagePath = "gifs/hello.gif"
        ↓
AsyncImage composable
        ↓
Coil loads image:
  1. Check memory cache
     ✓ Found? Use immediately
     ✗ Not found → Continue
  
  2. Check disk cache
     ✓ Found? Load to memory
     ✗ Not found → Continue
  
  3. Load from assets
     Read file: assets/gifs/hello.gif
     Decode: GIF decoder
     Cache: Store in memory & disk
  
  4. Display
     Show first frame initially
     If GIF: Animate frames automatically
```

### Why playbackSessionKey?

```
Problem: Image caching

Scenario:
  1. Display hello.gif
     Coil loads & caches
     Shows animation
  
  2. Stop playback
     AsyncImage still remembers hello.gif
  
  3. Restart playback
     Same image path "hello.gif"
     Coil sees cached version
     Shows same frame again (not fresh)

Solution: Change key
  AsyncImage(
    model = path,
    modifier = Modifier.key(playbackSessionKey)
  )
  
  When playbackSessionKey changes:
    → Compose treats as different item
    → Forces AsyncImage to reload image
    → Fresh animation starts
```

---

## 4.7 MATERIAL DESIGN 3 THEMING

### Theme Structure

```kotlin
object SignBuddyTheme {
    @Composable
    fun Get() {
        val colorScheme = if (isDarkTheme) DarkColors else LightColors
        
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
```

### Color System

```kotlin
MaterialTheme.colorScheme.apply {
    primary         // Main brand color (blue)
    secondary       // Secondary accent (teal)
    tertiary        // Third accent (orange)
    
    background     // App background
    surface        // Card/surface background
    
    error          // Error state (red)
    
    onPrimary      // Text/icons on primary color
    onSecondary    // Text/icons on secondary color
    onBackground   // Text on background
    onSurface      // Text on surface
}
```

### Using Theme Colors

```kotlin
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,  // Use theme blue
        contentColor = MaterialTheme.colorScheme.onPrimary   // Use theme text
    )
) {
    Text("Click Me")
}
```

**Benefits:**
- Single source of truth for colors
- Easy light/dark mode switching
- Consistent app appearance
- Accessibility (contrast checking)

---

## 4.8 COMPLETE UI FLOW DIAGRAM

```
┌──────────────────────────────────────────────────────┐
│              MAINACTIVITY (Compose)                  │
│                                                      │
│  setContent {                                        │
│    SignBuddyTheme {                                  │
│      ┌──────────────────────────────────────┐       │
│      │      PhoneScreen (Main Composable)   │       │
│      │                                      │       │
│      │  ┌────────────────────────────────┐ │       │
│      │  │ Header                         │ │       │
│      │  │ [Camera] [Info] [Guidelines]   │ │       │
│      │  └────────────────────────────────┘ │       │
│      │                                      │       │
│      │  ┌────────────────────────────────┐ │       │
│      │  │ Input Section                  │ │       │
│      │  │                                │ │       │
│      │  │ If listening:                  │ │       │
│      │  │  └─ ListeningCard              │ │       │
│      │  │     ├─ Animated indicator      │ │       │
│      │  │     └─ Status text             │ │       │
│      │  │                                │ │       │
│      │  │ Else:                          │ │       │
│      │  │  └─ TextInput field            │ │       │
│      │  │     └─ Manual text entry       │ │       │
│      │  └────────────────────────────────┘ │       │
│      │                                      │       │
│      │  ┌────────────────────────────────┐ │       │
│      │  │ Preview Section                │ │       │
│      │  │                                │ │       │
│      │  │ ┌──────────────────────────┐  │ │       │
│      │  │ │    SignPreview()         │  │ │       │
│      │  │ │                          │  │ │       │
│      │  │ │  [GIF Animation]         │  │ │       │
│      │  │ │  (hello.gif)             │  │ │       │
│      │  │ │                          │  │ │       │
│      │  │ │ Sign: hello              │  │ │       │
│      │  │ └──────────────────────────┘  │ │       │
│      │  │                                │ │       │
│      │  │ Text: "hello good morning"    │ │       │
│      │  └────────────────────────────────┘ │       │
│      │                                      │       │
│      │  ┌────────────────────────────────┐ │       │
│      │  │ Playback Controls              │ │       │
│      │  │ [Replay] [Play/Pause] [Loop]  │ │       │
│      │  └────────────────────────────────┘ │       │
│      │                                      │       │
│      │  ┌────────────────────────────────┐ │       │
│      │  │ Bottom Controls                │ │       │
│      │  │ [Mic Button] [Clear Button]   │ │       │
│      │  └────────────────────────────────┘ │       │
│      └──────────────────────────────────────┘       │
│  }                                                   │
│  }                                                   │
└──────────────────────────────────────────────────────┘
        ↑                                  ↑
        │ observes                        │ triggers callbacks
        │                                 │
┌────────────────────────────────────────┴──────┐
│         MAINVIEWMODEL                         │
│ ├─ var recognizedText                        │
│ ├─ var signSequence                          │
│ ├─ var currentIndex                          │
│ ├─ fun startPlayback()                       │
│ └─ fun translateAndPlay()                    │
└─────────────────────────────────────────────┘
```

---

## 4.9 RECOMPOSITION & PERFORMANCE

### Smart Recomposition

Compose only recomposes Composables whose parameters changed:

```kotlin
@Composable
fun PhoneScreen(viewModel: MainViewModel) {
    Column {
        // This only recomposes when recognizedText changes
        RecognizedTextDisplay(
            text = viewModel.recognizedText
        )
        
        // This only recomposes when signSequence changes
        SignPreview(
            sequence = viewModel.signSequence,
            currentIndex = viewModel.currentIndex
        )
        
        // This only recomposes when isLooping changes
        PlaybackControls(
            isLooping = viewModel.isLooping,
            onToggleLoop = { viewModel.isLooping = !viewModel.isLooping }
        )
    }
}

Timeline:
  recognizedText changes → RecognizedTextDisplay recomposes only
  currentIndex changes   → SignPreview recomposes only
  isLooping changes      → PlaybackControls recomposes only
  
  NOT affected:
    Everything else stays the same (no recomposition)
```

### Preventing Unnecessary Recompositions

```kotlin
// ❌ Bad: Creates new lambda every time
PlaybackControls(
    onToggleLoop = { viewModel.isLooping = !viewModel.isLooping }
)

// ✅ Good: Remember the lambda
val onToggleLoop = remember {
    { viewModel.isLooping = !viewModel.isLooping }
}
PlaybackControls(
    onToggleLoop = onToggleLoop
)
```

### Performance Metrics

| Operation | Time | Impact |
|-----------|------|--------|
| Initial composition | 200ms | App startup |
| Recompose (single param change) | 10-20ms | User interaction |
| Recompose (parameter list unchanged) | 0ms | Skipped |
| GIF animation | 16ms/frame at 60fps | Smooth playback |
| Image loading (cached) | <1ms | Instant |
| Image loading (network) | 500-2000ms | Async, no freeze |

---

## 4.10 ACCESSIBILITY IN COMPOSE

### Content Descriptions

```kotlin
AsyncImage(
    model = currentSign.imagePath,
    contentDescription = currentSign.label,  // For screen readers
    //                  ^^^^^^^^^^^^^^^^^^^^^^
    // "Read as: hello sign"
)
```

### Semantic Modifiers

```kotlin
Button(
    modifier = Modifier.semantics {
        contentDescription = "Start recording audio"
        testTag = "mic_button"
    }
) {
    Icon(Icons.Default.Mic)
}
```

### Keyboard Navigation

```kotlin
Button(
    onClick = { startListening() }
    // Automatically focusable via Tab key
) {
    Text("Start Listening")
}
```

---

## 4.11 DEBUGGING COMPOSE

### Logging Recompositions

```kotlin
@Composable
fun SignPreview(...) {
    Log.d("Recomposition", "SignPreview recomposed")
    // Add to understand recomposition count
}
```

### Layout Inspector

```
Android Studio → Tools → Layout Inspector
  Shows live Compose hierarchy
  Click items to see state
  Identify recomposition hotspots
```

---

## Summary: Complete Data Flow

```
┌─────────────────────────────────────────────────────────┐
│                 USER ACTION                              │
│              (Tap microphone button)                     │
└────────────────┬────────────────────────────────────────┘
                 ↓
        ┌────────────────┐
        │ MainActivity   │
        │                │
        │ startListening()
        └────────┬───────┘
                 ↓
        ┌────────────────────────────┐
        │ Android SpeechRecognizer   │
        │                            │
        │ (Captures audio)           │
        │ (Converts to text)         │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ RecognitionListener        │
        │                            │
        │ onResults(text)            │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ MainViewModel              │
        │                            │
        │ accumulatedText += text    │
        │ recognizedText = text      │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ Compose Recomposition      │
        │                            │
        │ PhoneScreen() called again │
        │ recognizedText changed     │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ UI Updated                 │
        │                            │
        │ RecognizedTextDisplay      │
        │ shows new text             │
        └────────────────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ User taps Translate        │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ MainViewModel              │
        │                            │
        │ translateAndPlay()         │
        │ (launches coroutine)       │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ Google Translate API       │
        │                            │
        │ POST request               │
        │ (on IO thread)             │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ Response Parsing           │
        │                            │
        │ Extract translatedText     │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ MainViewModel (Main thread)│
        │                            │
        │ recognizedText = translated│
        │ convertTextToISL()         │
        │ signSequence = [...]       │
        │ startPlayback()            │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ Compose Recomposition      │
        │                            │
        │ signSequence changed       │
        │ startPlayback triggered    │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ SignPreview Updated        │
        │                            │
        │ currentIndex = 0           │
        │ AsyncImage loads hello.gif │
        └────────┬───────────────────┘
                 ↓
        ┌────────────────────────────┐
        │ Animation Playing          │
        │                            │
        │ Every 2 seconds:           │
        │   currentIndex++           │
        │   New sign displayed       │
        └────────────────────────────┘
```

---

## Key Takeaways for Your Professor

1. **Declarative UI**: UI is function of state, not imperative commands
2. **Reactive State**: mutableStateOf() triggers automatic recomposition
3. **Efficient Rendering**: Only changed Composables recompose
4. **Modern Architecture**: Follows MVVM + reactive programming patterns
5. **Material Design 3**: Professional, accessible UI out of the box
6. **Async Operations**: Network calls don't freeze UI (Coroutines + viewModelScope)
7. **Image Handling**: Coil library handles caching, GIF decoding automatically
8. **Modularity**: Each Composable is independent, testable, reusable
