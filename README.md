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

Follow these steps to get the development environment ready:

### 1. Prerequisites
- **Android Studio Hedgehog (2023.1.1)** or newer.
- **JDK 17** (standard for modern Android projects).
- An Android device or emulator with **Google Play Services** (required for Speech Recognition).

### 2. Clone the Repository
```bash
git clone https://github.com/aarizkhan2407/Speech_Sign_Language_Project.git
cd Speech_Sign_Language_Project
```

### 3. Firebase Configuration
This project requires Firebase for authentication:
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Create a new project (e.g., "Signs and Speech").
3. Add an Android App to the project:
   - Package name: `com.example.speech_sign_language_project`
4. Download the `google-services.json` file.
5. Place the file in the `app/` directory of this project.
6. Enable **Email/Password** authentication in the Firebase Authentication settings.

### 4. Asset Requirements
The Sign Language translation engine (`ISLTranslator.kt`) expects sign language visual assets.
- Ensure your assets are located in the `app/src/main/res/drawable/` directory or configured in the translator logic.
- The default implementation maps common words to drawable resource IDs (e.g., `sign_hello`, `sign_thank_you`).

### 5. Build and Run
You can build the project using the Gradle wrapper:
```bash
# Build the debug APK
./gradlew assembleDebug

# Install and run on your connected device
./gradlew installDebug
```

## 📝 Usage

1. **Launch the App**: Open "Signs & Speech" on your device.
2. **Authentication**: Sign up for a new account or log in with existing credentials.
3. **Listen**: Press the large Blue Microphone button. The visualizer will show high-frequency activity as you speak.
4. **Visual Translate**: Recognized words will appear on screen, and if a corresponding sign exists in the library, it will be displayed automatically.
5. **Stop**: Press the Red Stop button to end the session.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---
