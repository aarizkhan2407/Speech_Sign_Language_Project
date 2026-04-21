# Signs & Speech: Real-time Communication Bridge

A modern Android application designed to bridge the communication gap between speech and sign language. This app leverages advanced Speech Recognition and a custom Sign Language translation engine to provide real-time, visual communication for users.

## 🚀 Features

- **Real-time Speech Recognition**: High-accuracy voice-to-text conversion using Google Speech API.
- **ISL (Indian Sign Language) Translation**: Translates recognized speech into corresponding sign language visual representations.
- **Modern UI/UX**: Built with Jetpack Compose, featuring high-quality animations, glassmorphism aesthetics, and a premium dark mode support.
- **Interactive Visualizers**: Includes dynamic voice frequency visualizers for real-time feedback during listening.
- **Secure Authentication**: Integrated Firebase Authentication for user accounts and data persistence.

## 🛠️ Technology Stack

- **Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for a modern, declarative UI.
- **Language**: [Kotlin](https://kotlinlang.org/) (Coroutines, StateFlow, ViewModel).
- **Backend/Auth**: [Firebase](https://firebase.google.com/) (Email/Password & Google Sign-In).
- **Speech API**: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/) for performant asynchronous sign visual loading.
- **Architecture**: MVVM (Model-View-ViewModel) following standard Android development best practices.

## 📦 Project Structure

- `app/src/main/java/com/example/speech_sign_language_project/`
  - `MainActivity.kt`: The primary communication interface.
  - `MainViewModel.kt`: Core logic for speech-to-sign conversion.
  - `Components.kt`: Highly stylized reusable UI components (Mic Button, Visualizers, etc.).
  - `ISLTranslator.kt`: The logic engine for mapping speech text to sign assets.
  - `AuthActivities.kt`: Handle Login, Signup, and Authentication flows.

## ⚙️ Setup & Installation

1. **Clone the repository**:
   ```bash
   git clone [repository-url]
   ```
2. **Open in Android Studio**:
   Use Android Studio Hedgehog (or later) for full Compose support.
3. **Configure Firebase**:
   - Add your `google-services.json` to the `app/` directory.
   - Enable Authentication in your Firebase Console.
4. **Build and Run**:
   Click "Run app" or use Gradle:
   ```bash
   ./gradlew installDebug
   ```

## 📝 Usage

1. Open the app and log in or create an account.
2. Tap the blue wide Microphone button to start listening.
3. Speak clearly; the app will convert your speech to text and then show the corresponding Sign Language images in real-time.
4. Tap the red button to stop listening.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

*Developed with ❤️ as part of the Advanced Agentic Coding effort.*
