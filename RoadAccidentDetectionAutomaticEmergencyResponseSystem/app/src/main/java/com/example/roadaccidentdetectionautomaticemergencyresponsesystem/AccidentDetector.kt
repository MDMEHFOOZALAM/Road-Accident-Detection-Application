package com.example.roadaccidentdetectionautomaticemergencyresponsesystem

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class AccidentDetector(
    context: Context,
    private val threshold: Float = 35f,
    private val onAccidentDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var isTriggered = false
    private var lastTriggerTime = 0L

    fun start() {
        isTriggered = false
        lastTriggerTime = 0L
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = sqrt(x * x + y * y + z * z)
            val currentTime = System.currentTimeMillis()

            // Cooldown: 30 seconds to prevent spamming
            if (acceleration > threshold && !isTriggered && (currentTime - lastTriggerTime > 30000)) {
                lastTriggerTime = currentTime
                isTriggered = true
                onAccidentDetected()

                // Reset trigger state after 10 seconds to allow for future detections
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isTriggered = false
                }, 10000)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
