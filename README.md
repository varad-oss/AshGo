# 🛡️ Ash Go: Active Protection System

Ash Go is a modern, real-time location tracking and personal safety application built with Android Jetpack Compose and Firebase. It features a two-sided architecture connecting a "Traveler" with a "Guardian/Receiver" to ensure maximum safety during transit.

## ✨ Features
* **Real-time Live Tracking:** High-frequency location telemetry broadcasting via Firebase Realtime Database.
* **Persistent Foreground Service:** Continues to track reliably even when the app is minimized, closed, or the screen is locked.
* **Guardian Dashboard:** A dedicated receiver UI featuring split-screen map tracking, vehicle registration logging, and last-ping timestamps.
* **OpenStreetMap Integration:** Fully interactive mapping using `osmdroid` to avoid API key billing constraints.
* **Emergency SOS:** Instant one-button alerting system that turns the Receiver dashboard red and triggers high-priority notifications.
* **Integrated Live Chat:** Built-in messaging so the traveler and receiver can communicate without switching apps.

## 🚀 Getting Started

1. **Clone the repository:**
   ```bash
   git clone https://github.com/varad-oss/AshGo.git
   ```
2. **Add Firebase Credentials:**
   - Create a project in the Firebase Console.
   - Download the `google-services.json` file.
   - Place it in the `app/` directory of the project.
3. **Build and Run:**
   Open the project in Android Studio and hit **Run** (`Shift + F10`).

## 🛠️ Tech Stack
* **UI:** Jetpack Compose (Material 3)
* **Backend:** Firebase Realtime Database
* **Mapping:** `osmdroid` (OpenStreetMap)
* **Architecture:** Foreground Services, Kotlin Coroutines, Service Binding
