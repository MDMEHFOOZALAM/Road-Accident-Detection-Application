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

class AccidentDetectionService : Service() {

    private lateinit var accidentDetector: AccidentDetector
    private lateinit var emergencyManager: EmergencyManager
    private val CHANNEL_ID = "AccidentDetectionChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        emergencyManager = EmergencyManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring for accidents...")
        startForeground(NOTIFICATION_ID, notification)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val threshold = prefs.getFloat("impact_sensitivity", 35f)

        accidentDetector = AccidentDetector(this, threshold) {
            triggerEmergencyProtocol()
        }
        accidentDetector.start()

        return START_STICKY
    }

    private fun triggerEmergencyProtocol() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(1000)

        // Broadcast to Activity that an accident was detected
        val broadcastIntent = Intent("com.example.ACCIDENT_DETECTED")
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)

        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val currentDate = sdf.format(Date())
        
        // Save initial incident as "Processing..."
        saveIncident(Incident(currentDate, "Impact Detected", "Processing..."))
        sendBroadcast(Intent("com.example.HISTORY_UPDATED").apply { setPackage(packageName) })

        val contacts = loadContacts()
        if (contacts.isNotEmpty()) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val savedDelay = prefs.getInt("sos_delay", 5)
            
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({
                val isCancelled = prefs.getBoolean("is_emergency_cancelled", false)
                
                if (!isCancelled) {
                    emergencyManager.triggerEmergency(contacts.map { it.phone })
                    updateNotification("Emergency Alerts Sent!")
                    updateLastIncidentStatus("Sent")
                } else {
                    prefs.edit().putBoolean("is_emergency_cancelled", false).apply()
                    updateNotification("Emergency Alert Cancelled.")
                    updateLastIncidentStatus("Cancelled")
                }
                
                // Notify UI that emergency was sent successfully
                val sentIntent = Intent("com.example.EMERGENCY_SENT")
                sentIntent.setPackage(packageName)
                sendBroadcast(sentIntent)
                
                // Broadcast to UI to refresh history
                val updateIntent = Intent("com.example.HISTORY_UPDATED")
                updateIntent.setPackage(packageName)
                sendBroadcast(updateIntent)
            }, (savedDelay * 1000).toLong())
        }
    }

    private fun updateLastIncidentStatus(newStatus: String) {
        val prefs = getSharedPreferences("history_prefs", MODE_PRIVATE)
        val json = prefs.getString("incidents_list", null)
        val type = object : TypeToken<MutableList<Incident>>() {}.type
        val incidents: MutableList<Incident> = if (json != null) {
            Gson().fromJson(json, type)
        } else {
            mutableListOf()
        }
        
        if (incidents.isNotEmpty()) {
            val lastIncident = incidents.last()
            incidents[incidents.size - 1] = lastIncident.copy(status = newStatus)
            prefs.edit().putString("incidents_list", Gson().toJson(incidents)).commit()
        }
    }

    private fun saveIncident(incident: Incident) {
        val prefs = getSharedPreferences("history_prefs", MODE_PRIVATE)
        val json = prefs.getString("incidents_list", null)
        val type = object : TypeToken<MutableList<Incident>>() {}.type
        val incidents: MutableList<Incident> = if (json != null) {
            Gson().fromJson(json, type)
        } else {
            mutableListOf()
        }
        incidents.add(incident)
        prefs.edit().putString("incidents_list", Gson().toJson(incidents)).commit()
    }

    private fun loadContacts(): List<EmergencyContact> {
        val prefs = getSharedPreferences("contacts_prefs", MODE_PRIVATE)
        val json = prefs.getString("contacts_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<EmergencyContact>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Monitor")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Accident Detection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::accidentDetector.isInitialized) {
            accidentDetector.stop()
        }
        super.onDestroy()
    }
}
