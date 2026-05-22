# Road Accident Detection & Automatic Emergency Response System 🚗🚨

An Android application built with **Kotlin** and **Jetpack Compose** designed to improve road safety. The app utilizes the smartphone's built-in accelerometer sensor to detect severe impacts indicating a potential vehicle accident. Once triggered, it launches an emergency protocol to automatically send SOS alerts containing live GPS tracking links to preset emergency contacts.

---

## 🌟 Features

* 🏃 **Background Monitoring:** Runs as an Android Foreground Service to maintain safety tracking even when the app is minimized or the phone is locked.
* 📐 **Intelligent Impact Detection:** Real-time multi-axis ($X, Y, Z$) accelerometer tracking utilizing combined vector magnitude calculation to isolate major impact forces.
* ⏱️ **Smart SOS Countdown:** An automated warning dialog with a customizable delay timer and haptic vibration feedback, allowing users to safely cancel false alarms.
* 📍 **High-Accuracy Live Location:** Integrates Google Play Services Location API to capture precise real-time coordinates.
* 💬 **Background SMS Dispatch:** Automatically formats and transmits localized SOS messages containing clickable Google Maps links completely in the background.
* ⚙️ **Customizable Sensitivity:** Configurable impact thresholds (High, Medium, Low) to tailor detection accuracy to different driving styles and vehicular environments.
* 📋 **Incident Ledger:** Keeps a fully local, persistent history of all detected incidents, manual SOS triggers, and emergency statuses.

---

## 🛠️ Architecture & Core Components

The application is modularized into dedicated structural components handling hardware tracking, UI states, and system notifications:

| Component | Responsibility |
| :--- | :--- |
| **`MainActivity.kt`** | Manages application lifecycle, multi-screen runtime permission requests, persistent local database states, and the modern Jetpack Compose UI layout. |
| **`AccidentDetectionService.kt`** | An independent Foreground Service ensuring uninterrupted background physical sensor telemetry. |
| **`AccidentDetector.kt`** | Implements `SensorEventListener` to parse raw accelerometer readings and filter micro-spikes via a 30-second localized cooldown loop. |
| **`EmergencyManager.kt`** | Interacts with the fused location provider client and manages programmatic multi-part SMS configurations. |

---

## 📡 Hardware Telemetry Calculation

The hardware sensor layer utilizes the physics distance formula to monitor absolute velocity changes across all 3 spatial planes:

$$\text{Force Amplitude} = \sqrt{x^2 + y^2 + z^2}$$

If the total calculated amplitude crosses the user-specified threshold, the app triggers the safety pipeline.

---

## 🚀 Getting Started

### Prerequisites
* Android Studio Ladybug or newer
* Android SDK 34 (Compiled & Targeted)
* A physical Android device equipped with a functional Accelerometer sensor and an active cellular SIM card (required for background SMS dispatch)

### Required Permissions
To ensure full operational functionality, the application requests the following runtime Android permissions:
* `Manifest.permission.ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` (Satellite mapping accuracy)
* `Manifest.permission.SEND_SMS` (Automated messaging capabilities)
* `Manifest.permission.POST_NOTIFICATIONS` (Foreground Service visibility standard for Android 13+)

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/mdmehfoozalam/road-accident-detection-application.git](https://github.com/mdmehfoozalam/road-accident-detection-application.git)
