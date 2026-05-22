# 🚗🚨 Road Accident Detection Application (ELI5 Edition!)

Welcome to the **Road Accident Detection App**!

This is a special version of the app where the code and this explanation are written so simply that almost anyone can understand how it works—like explaining it to a 5-year-old (ELI5)!

## 🦸‍♂️ What does this app do?

Imagine you are driving a car. This app acts like a tiny, invisible superhero sitting in your phone. Its only job is to make sure you are safe.

If the superhero feels the car stop *very* suddenly (like in a crash), it automatically grabs its walkie-talkie and texts your friends and family to say, *"Help! I think there was a crash! Here is exactly where we are on the map!"*

## 🧩 How does it work? (The Secret Pieces)

The app is built using **Kotlin** (a language computers understand) and **Jetpack Compose** (a way to draw the buttons and screens). We have broken the app down into three main helpers:

1. **The Watch Guard (`AccidentDetector.kt`)**
   - The phone has a tiny tool inside it called an *accelerometer*. It can feel movement. The Watch Guard holds this tool and pays close attention. If the movement is too violent, the Watch Guard yells, "ALARM!"

2. **The Emergency Dispatcher (`EmergencyManager.kt`)**
   - When the Watch Guard yells "ALARM!", the Dispatcher wakes up. First, it asks the phone for a map to find out exactly where you are. Then, it silently sends text messages (SMS) to the emergency contacts you saved, asking for help.

3. **The Background Worker (`AccidentDetectionService.kt`)**
   - You don't want to keep your phone screen on all the time while driving. The Background Worker's job is to stay awake even when the app is closed. It keeps the Watch Guard active in the background so you are always protected.

## 🛠️ Features

*   **Automatic Crash Detection:** Feels big bumps and crashes automatically.
*   **Live GPS Location:** Sends a Google Maps link to your friends so they can find you.
*   **Manual SOS Button:** A big red button you can press yourself if you need help and the phone didn't feel it.
*   **Incident History:** A diary that remembers every time the alarm went off.
*   **Adjustable Sensitivity:** You can tell the Watch Guard to be very sensitive (for bumpy roads) or less sensitive (for smooth roads).

## 🚀 How to build and run the app

If you want to put this app on your phone, follow these steps:

1. Open this folder in a program called **Android Studio** (it's a tool for making apps).
2. Let Android Studio load all the pieces (this takes a minute).
3. Plug your Android phone into your computer with a cable.
4. Press the big green **Play button** (▶️) at the top of Android Studio.
5. The app will magicaly appear on your phone!

---
*Stay safe out there!* 🚗🛡️