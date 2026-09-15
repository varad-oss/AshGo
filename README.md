# AshGo 

> A completely private, real-time safety tracking and connection app built natively for Android.

##  Overview
AshGo is a bespoke Android application designed with a single purpose: to provide absolute peace of mind and seamless connection between two individuals. Built natively in Kotlin using **Jetpack Compose**, it completely bypasses the need for third-party commercial tracking apps, offering a 100% private, self-hosted environment where data is never shared.

It features robust background location tracking, real-time chat, emergency SOS broadcasting, and a custom in-house auto-updater system.

##  Core Features

###  Real-Time Safety & Tracking
* **Live Background Location:** Streams high-accuracy GPS coordinates to Firebase via Foreground Services, allowing the receiver to monitor active trips in real-time.
* **Emergency SOS System:** A prominent panic button that broadcasts an immediate alert, triggers a local alarm, and automatically dispatches SMS messages containing a Google Maps link to predefined emergency contacts.
* **Dead-Signal Detection:** Automatically calculates timestamp deltas and warns the receiver if the traveler loses cellular signal or goes offline for more than 5 minutes.
* **Fake Call Simulator:** Triggers a realistic simulated incoming phone call to help the user gracefully exit uncomfortable situations.

###  Private Connection
* **The "Thunder" Button:** A real-time, zero-latency "buzz" feature that sends a high-priority push notification and shock sound to the other user's device instantly.
* **Built-in Chat Room:** A private, WhatsApp-style instant messaging interface with image/selfie support and unread message badging.
* **Optimized Image Handling:** In-app selfie capture utilizes on-the-fly matrix scaling and aggressive JPEG compression to transmit images purely via Base64 strings, keeping database usage ultra-lean.

##  Technical Architecture

* **UI Framework:** Jetpack Compose (Material 3)
* **Language:** Kotlin
* **Backend:** Firebase Realtime Database
* **Architecture Highlights:**
  * **Custom Auto-Updater:** Bypasses the Google Play Store entirely. The app continuously listens to a Firebase `app_config` node and automatically downloads and installs new APKs from a private GitHub Release repository when the version code bumps.
  * **Foreground Services:** Utilizes robust Android 14 (`targetSdk=34`) `location` and `dataSync` Foreground Services to guarantee uninterrupted location broadcasting and Firebase listening even when the app is swiped away or the device is locked.
  * **Aggressive Data Hygiene:** Implements a background routine on every app launch that prunes old database entries (chats > 3 days, alerts > 24 hours). Combined with location data that overwrites itself rather than accruing, the app is engineered to stay within the Firebase 1GB free tier perpetually.

##  Privacy First
Unlike commercial tracking apps, AshGo does not monetize, harvest, or store historical location data. Location nodes overwrite themselves in real-time. There is no user registration, no emails, and no passwords—the client environment is completely locked to the two predefined users.

---
*Made with ❤️, by Varad, for his ❤️.*
