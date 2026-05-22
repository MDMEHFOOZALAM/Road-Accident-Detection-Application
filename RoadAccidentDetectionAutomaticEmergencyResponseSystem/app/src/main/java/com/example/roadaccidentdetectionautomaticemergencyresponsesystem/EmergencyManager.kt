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

class EmergencyManager(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun triggerEmergency(phoneNumbers: List<String>) {
        if (phoneNumbers.isEmpty()) {
            Log.w("EmergencyManager", "No phone numbers provided")
            return
        }

        // Check permission again just in case
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e("EmergencyManager", "SEND_SMS permission not granted")
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val message = if (location != null) {
                    "🚨 EMERGENCY! Accident detected.\nLocation: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else {
                    "🚨 EMERGENCY! Accident detected. Location unavailable."
                }

                Log.d("EmergencyManager", "Preparing to send SMS to ${phoneNumbers.size} contacts")
                // Send SMS to all contacts
                phoneNumbers.forEach { number ->
                    Log.d("EmergencyManager", "Triggering background SMS for: $number")
                    sendSms(number, message)
                }
            }
            .addOnFailureListener { e ->
                Log.e("EmergencyManager", "Location fetch failed", e)
                val fallbackMessage = "🚨 EMERGENCY! Accident detected. Location failed."
                phoneNumbers.forEach { number ->
                    sendSms(number, fallbackMessage)
                }
            }
    }

    private fun shareToMessageApp(message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:") // This ensures only SMS apps handle this
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Sharing to messaging app failed", e)
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            val formattedNumber = if (cleanNumber.startsWith("+")) {
                cleanNumber
            } else if (cleanNumber.length == 10) {
                "+91$cleanNumber"
            } else {
                cleanNumber
            }

            val parts = smsManager?.divideMessage(message)
            if (parts != null && smsManager != null) {
                smsManager.sendMultipartTextMessage(formattedNumber, null, parts, null, null)
                Log.d("EmergencyManager", "Multi-part SMS sent successfully to $formattedNumber")
            } else {
                smsManager?.sendTextMessage(formattedNumber, null, message, null, null)
                Log.d("EmergencyManager", "SMS sent successfully to $formattedNumber")
            }
        } catch (e: Exception) {
            Log.e("EmergencyManager", "SMS sending failed for $phoneNumber", e)
        }
    }
}
