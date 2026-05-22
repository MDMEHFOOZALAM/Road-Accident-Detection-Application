package com.example.roadaccidentdetectionautomaticemergencyresponsesystem

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * EmergencyManager is like an emergency dispatcher.
 * When an accident happens, its job is to find out where you are (your location)
 * and send text messages (SMS) to your emergency contacts to ask for help!
 */
class EmergencyManager(private val appContext: Context) {

    // A tool from Google to figure out exactly where the phone is on the map right now.
    private val locationFinder = LocationServices.getFusedLocationProviderClient(appContext)

    /**
     * "Start the emergency protocol!"
     * This function is called when a crash is detected. It takes a list of phone numbers.
     */
    @SuppressLint("MissingPermission")
    fun triggerEmergency(emergencyPhoneNumbers: List<String>) {

        // If we don't have any phone numbers to call, we can't do anything! So we stop.
        if (emergencyPhoneNumbers.isEmpty()) {
            Log.w("EmergencyManager", "No phone numbers provided")
            return
        }

        // Before sending messages, we must ask the phone: "Do we have permission to send SMS text messages?"
        // If the answer is no, we stop and log an error.
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("EmergencyManager", "SEND_SMS permission not granted")
            return
        }

        // We ask the location finder to get our exact location as accurately as possible.
        locationFinder.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { exactLocation ->

                // If it successfully finds our location...

                // Create the text message.
                val textMessage = if (exactLocation != null) {
                    // We found the location! Create a message with a Google Maps link.
                    "🚨 EMERGENCY! Accident detected.\nLocation: https://maps.google.com/?q=${exactLocation.latitude},${exactLocation.longitude}"
                } else {
                    // We couldn't find the exact location, so we send a message without it.
                    "🚨 EMERGENCY! Accident detected. Location unavailable."
                }

                Log.d("EmergencyManager", "Preparing to send SMS to ${emergencyPhoneNumbers.size} contacts")

                // Go through every phone number in our list and send them the message.
                emergencyPhoneNumbers.forEach { phoneNumber ->
                    Log.d("EmergencyManager", "Triggering background SMS for: $phoneNumber")
                    sendSmsMessage(phoneNumber, textMessage)
                }
            }
            .addOnFailureListener { exception ->
                // Uh oh, the location finder failed completely!
                Log.e("EmergencyManager", "Location fetch failed", exception)

                // We still want to ask for help, so we create a fallback message without the location.
                val fallbackMessage = "🚨 EMERGENCY! Accident detected. Location failed."

                // Go through every phone number in our list and send them the fallback message.
                emergencyPhoneNumbers.forEach { phoneNumber ->
                    sendSmsMessage(phoneNumber, fallbackMessage)
                }
            }
    }

    /**
     * This function opens up the phone's messaging app with a message ready to send.
     * We don't actually use it for the automatic alerts, but it's here as a backup tool!
     */
    private fun shareToMessageApp(textMessage: String) {
        try {
            // "Intent" means "Intention". We are creating an intention to send something.
            val intention = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:") // This tells the phone to open an SMS messaging app.
                putExtra("sms_body", textMessage) // We give the app the text message.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Start the messaging app!
            appContext.startActivity(intention)
        } catch (exception: Exception) {
            Log.e("EmergencyManager", "Sharing to messaging app failed", exception)
        }
    }

    /**
     * This is the function that actually sends the text message silently in the background.
     */
    private fun sendSmsMessage(phoneNumber: String, textMessage: String) {
        try {
            // First, get the phone's SMS manager (the part of the phone that handles text messages).
            // The way we ask for it is slightly different depending on how new the phone's software is.
            val messageManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Remove any spaces, dashes, or weird characters from the phone number so it's just numbers and maybe a plus sign (+).
            val cleanPhoneNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

            // Format the phone number to make sure it works in India (by adding +91 if needed).
            val perfectlyFormattedNumber = if (cleanPhoneNumber.startsWith("+")) {
                cleanPhoneNumber
            } else if (cleanPhoneNumber.length == 10) {
                "+91$cleanPhoneNumber"
            } else {
                cleanPhoneNumber
            }

            // Sometimes a message is too long to send as one piece. We ask the manager to divide it up.
            val messageParts = messageManager?.divideMessage(textMessage)

            // If the message had to be divided into parts, send all the parts together.
            if (messageParts != null && messageManager != null) {
                messageManager.sendMultipartTextMessage(perfectlyFormattedNumber, null, messageParts, null, null)
                Log.d("EmergencyManager", "Multi-part SMS sent successfully to $perfectlyFormattedNumber")
            } else {
                // If it's short enough, just send it as one single message.
                messageManager?.sendTextMessage(perfectlyFormattedNumber, null, textMessage, null, null)
                Log.d("EmergencyManager", "SMS sent successfully to $perfectlyFormattedNumber")
            }
        } catch (exception: Exception) {
            // If something goes completely wrong while trying to send the text message, record the error.
            Log.e("EmergencyManager", "SMS sending failed for $phoneNumber", exception)
        }
    }
}
