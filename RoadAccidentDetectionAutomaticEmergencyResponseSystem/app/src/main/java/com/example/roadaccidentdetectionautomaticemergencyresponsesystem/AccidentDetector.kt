package com.example.roadaccidentdetectionautomaticemergencyresponsesystem

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * AccidentDetector acts like a watch guard.
 * Imagine it holds a special tool (accelerometer) that feels how fast the phone is moving.
 * If the phone gets shaken or stopped very suddenly (like in a crash), it rings an alarm!
 */
class AccidentDetector(
    // The environment where our app runs, like the house the guard lives in.
    appContext: Context,

    // The amount of force needed to think it's a real crash.
    // Think of it as: "How hard does the phone need to be hit?"
    private val crashImpactLevel: Float = 35f,

    // The alarm button! This is a set of instructions we run when a crash is detected.
    private val soundTheAlarm: () -> Unit
) : SensorEventListener { // SensorEventListener means "I am listening to changes in the phone's movement sensors!"

    // We ask the phone's system (the house) for the manager in charge of all sensors.
    private val movementManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // We specifically ask the manager for the "Accelerometer", the tool that measures speed and shaking.
    private val shakeSensor = movementManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Have we already pressed the alarm button recently?
    private var isAlarmRingingRightNow = false

    // A clock to remember exactly when we last pressed the alarm button.
    private var timeOfLastCrash = 0L

    /**
     * "Start the watch guard!"
     * This tells our app to start listening to the shake sensor.
     */
    fun start() {
        // Reset our memory when we start
        isAlarmRingingRightNow = false
        timeOfLastCrash = 0L

        // If the phone has a shake sensor, tell the manager we want to listen to it normally.
        if (shakeSensor != null) {
            movementManager.registerListener(this, shakeSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * "Stop the watch guard!"
     * This tells our app to stop listening. We can rest now.
     */
    fun stop() {
        // Tell the manager we are no longer listening to the sensors.
        movementManager.unregisterListener(this)
    }

    /**
     * This special function is automatically called every time the shake sensor feels movement!
     */
    override fun onSensorChanged(movementEvent: SensorEvent?) {
        // Did this movement come from the shake sensor?
        if (movementEvent?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {

            // The sensor gives us movement in 3 directions: Left/Right, Up/Down, Forward/Backward.
            val movementLeftRight = movementEvent.values[0]
            val movementUpDown = movementEvent.values[1]
            val movementForwardBack = movementEvent.values[2]

            // We use math (Pythagorean theorem) to combine these 3 directions into one big number representing total force!
            val totalForce = sqrt(movementLeftRight * movementLeftRight +
                                  movementUpDown * movementUpDown +
                                  movementForwardBack * movementForwardBack)

            // What time is it exactly right now?
            val timeRightNow = System.currentTimeMillis()

            // We check three rules to see if we should sound the alarm:
            // 1. Is the total force bigger than our "crash impact level"?
            // 2. Is the alarm NOT ringing right now?
            // 3. Has it been more than 30 seconds (30,000 milliseconds) since the last crash? (We wait so we don't spam messages).
            if (totalForce > crashImpactLevel && !isAlarmRingingRightNow && (timeRightNow - timeOfLastCrash > 30000)) {

                // Rule passed! A crash was detected!

                // Remember the time this happened.
                timeOfLastCrash = timeRightNow

                // Remember that the alarm is currently ringing.
                isAlarmRingingRightNow = true

                // SOUND THE ALARM! (Run the instructions given to us when creating this class).
                soundTheAlarm()

                // We want to be able to detect crashes again in the future.
                // So, we set a little timer. After 10 seconds (10,000 milliseconds)...
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // ...we say the alarm is no longer ringing, ready for the next crash.
                    isAlarmRingingRightNow = false
                }, 10000)
            }
        }
    }

    /**
     * This function is also required, but we don't care if the sensor gets more or less accurate,
     * so we leave it empty!
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing!
    }
}
