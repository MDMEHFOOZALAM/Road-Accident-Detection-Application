package com.example.roadaccidentdetectionautomaticemergencyresponsesystem

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * AccidentDetectionService is a special "Background Worker" for Android.
 * It stays awake even when you close the app, running quietly in the background
 * to make sure you are safe while driving!
 */
class AccidentDetectionService : Service() {

    // Our watch guard from the other file!
    private lateinit var accidentDetector: AccidentDetector

    // Our emergency dispatcher from the other file!
    private lateinit var emergencyManager: EmergencyManager

    // An ID for the sticky notification that shows the app is running in the background.
    private val NOTIFICATION_CHANNEL_ID = "AccidentDetectionChannel"
    private val NOTIFICATION_ID = 1

    /**
     * This is called once when the background worker first wakes up.
     */
    override fun onCreate() {
        super.onCreate()
        // Wake up the emergency manager so it's ready to send texts.
        emergencyManager = EmergencyManager(this)

        // Android requires apps running in the background to show a notification.
        // We set up the channel for that notification here.
        createNotificationChannel()
    }

    /**
     * This is called every time we tell the worker to "Start working!"
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Create the sticky notification that says "Monitoring for accidents..."
        val notificationMessage = createNotification("Monitoring for accidents...")

        // Tell Android: "Hey, I am doing important work! Don't kill me! Here is my notification to prove it."
        startForeground(NOTIFICATION_ID, notificationMessage)

        // Read the user's saved settings (the "app_prefs" file).
        val userSettings = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Find out how hard a crash needs to be to trigger an alarm (default is 35).
        val crashSensitivityThreshold = userSettings.getFloat("impact_sensitivity", 35f)

        // Give the watch guard the sensitivity setting and tell it what to do if it finds a crash.
        accidentDetector = AccidentDetector(this, crashSensitivityThreshold) {
            // When a crash happens, do this!
            triggerEmergencyProtocol()
        }

        // Start watching!
        accidentDetector.start()

        // "START_STICKY" means if Android accidentally kills this worker to save memory,
        // it should restart it as soon as possible.
        return START_STICKY
    }

    /**
     * This is the BIG ALARM function. It's called when the watch guard detects a real crash.
     */
    private fun triggerEmergencyProtocol() {
        // 1. Make the phone vibrate really hard for 1 second (1000 milliseconds) so the user notices!
        val phoneVibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        phoneVibrator.vibrate(1000)

        // 2. Shout to the main app screen: "HEY! AN ACCIDENT HAPPENED!"
        // (This makes the countdown screen pop up).
        val accidentBroadcast = Intent("com.example.ACCIDENT_DETECTED")
        accidentBroadcast.setPackage(packageName)
        sendBroadcast(accidentBroadcast)

        // 3. Write down the time the crash happened for the history book.
        val timeFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateAndTimeRightNow = timeFormatter.format(Date())

        // Save this crash in the history as "Processing..." (meaning we haven't sent texts yet).
        saveIncidentToHistory(Incident(dateAndTimeRightNow, "Impact Detected", "Processing..."))

        // Tell the history screen to refresh itself.
        sendBroadcast(Intent("com.example.HISTORY_UPDATED").apply { setPackage(packageName) })

        // 4. Get the list of emergency contacts.
        val savedContacts = loadContacts()

        // If the user actually added some contacts...
        if (savedContacts.isNotEmpty()) {
            val userSettings = getSharedPreferences("app_prefs", MODE_PRIVATE)

            // Check how many seconds the user wants us to wait before sending texts (e.g., 5 seconds).
            val secondsToWait = userSettings.getInt("sos_delay", 5)

            // Set a timer!
            val timer = android.os.Handler(android.os.Looper.getMainLooper())
            timer.postDelayed({

                // TIMES UP! Check if the user pressed the "I AM SAFE (Cancel)" button on the screen.
                val didUserCancel = userSettings.getBoolean("is_emergency_cancelled", false)

                if (!didUserCancel) {
                    // The user DID NOT cancel. This is a real emergency!
                    // Tell the emergency manager to send texts to all contacts!
                    emergencyManager.triggerEmergency(savedContacts.map { it.phone })

                    // Update the notification to say texts were sent.
                    updateNotificationMessage("Emergency Alerts Sent!")

                    // Update the history book to say "Sent".
                    updateLastIncidentStatusInHistory("Sent")
                } else {
                    // The user pressed CANCEL! It was a false alarm.
                    // Reset the cancel button for next time.
                    userSettings.edit().putBoolean("is_emergency_cancelled", false).apply()

                    // Update the notification to say it was cancelled.
                    updateNotificationMessage("Emergency Alert Cancelled.")

                    // Update the history book to say "Cancelled".
                    updateLastIncidentStatusInHistory("Cancelled")
                }

                // Shout to the app screen: "Hey, we finished the emergency stuff!"
                val finishedBroadcast = Intent("com.example.EMERGENCY_SENT")
                finishedBroadcast.setPackage(packageName)
                sendBroadcast(finishedBroadcast)

                // Shout to the history screen: "Hey, refresh yourself again, the status changed!"
                val updateHistoryBroadcast = Intent("com.example.HISTORY_UPDATED")
                updateHistoryBroadcast.setPackage(packageName)
                sendBroadcast(updateHistoryBroadcast)

            }, (secondsToWait * 1000).toLong()) // Convert seconds to milliseconds (5s = 5000ms)
        }
    }

    /**
     * Updates the last entry in our history book with a new status (like "Sent" or "Cancelled").
     */
    private fun updateLastIncidentStatusInHistory(newStatus: String) {
        val historyBook = getSharedPreferences("history_prefs", MODE_PRIVATE)
        val historyText = historyBook.getString("incidents_list", null)

        // This tells the app how to read a list of 'Incident' objects from text.
        val listType = object : TypeToken<MutableList<Incident>>() {}.type

        val listOfIncidents: MutableList<Incident> = if (historyText != null) {
            Gson().fromJson(historyText, listType) // Read the text into a real list.
        } else {
            mutableListOf() // Create an empty list.
        }

        if (listOfIncidents.isNotEmpty()) {
            // Find the very last thing that happened.
            val lastThingThatHappened = listOfIncidents.last()

            // Change its status.
            listOfIncidents[listOfIncidents.size - 1] = lastThingThatHappened.copy(status = newStatus)

            // Save the list back into the book!
            historyBook.edit().putString("incidents_list", Gson().toJson(listOfIncidents)).commit()
        }
    }

    /**
     * Adds a completely new incident to our history book.
     */
    private fun saveIncidentToHistory(newIncident: Incident) {
        val historyBook = getSharedPreferences("history_prefs", MODE_PRIVATE)
        val historyText = historyBook.getString("incidents_list", null)

        val listType = object : TypeToken<MutableList<Incident>>() {}.type
        val listOfIncidents: MutableList<Incident> = if (historyText != null) {
            Gson().fromJson(historyText, listType)
        } else {
            mutableListOf()
        }

        // Add the new incident to the end of the list.
        listOfIncidents.add(newIncident)

        // Save the list back into the book.
        historyBook.edit().putString("incidents_list", Gson().toJson(listOfIncidents)).commit()
    }

    /**
     * Reads the list of emergency contacts the user saved.
     */
    private fun loadContacts(): List<EmergencyContact> {
        val contactsBook = getSharedPreferences("contacts_prefs", MODE_PRIVATE)
        val contactsText = contactsBook.getString("contacts_list", null)

        return if (contactsText != null) {
            val listType = object : TypeToken<List<EmergencyContact>>() {}.type
            Gson().fromJson(contactsText, listType) // Turn text into a real list of contacts.
        } else {
            emptyList() // Return an empty list if there are no contacts.
        }
    }

    /**
     * Creates the little box that shows up in the phone's notification bar at the top of the screen.
     */
    private fun createNotification(message: String): Notification {
        // If the user taps the notification, open the main app screen.
        val openAppIntent = Intent(this, MainActivity::class.java)
        val clickableIntent = PendingIntent.getActivity(this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        // Build the notification with a title, message, and icon.
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Safety Monitor")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(clickableIntent)
            .build()
    }

    /**
     * Changes the text on the notification box that is currently showing.
     */
    private fun updateNotificationMessage(newMessage: String) {
        val newNotificationBox = createNotification(newMessage)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Replace the old notification (ID 1) with the new one.
        notificationManager.notify(NOTIFICATION_ID, newNotificationBox)
    }

    /**
     * Modern Android phones require notifications to be put into "Channels" (like TV channels).
     * This creates the channel so our notifications actually show up.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Accident Detection Service",
                NotificationManager.IMPORTANCE_LOW // Low importance means it won't beep or vibrate.
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * This is needed for technical reasons, but we don't use it, so we return null.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * This is called when the background worker is told to finally go to sleep (killed).
     */
    override fun onDestroy() {
        // Tell the watch guard to stop looking for crashes.
        if (::accidentDetector.isInitialized) {
            accidentDetector.stop()
        }
        super.onDestroy()
    }
}
