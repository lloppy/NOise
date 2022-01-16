///////////////////////////////////////////////////////////////////////////////
package com.ahandyapp.airnavx.ui.sense


import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.fragment.app.FragmentActivity
import kotlin.math.PI

class AngleMeter: SensorEventListener {

    private val TAG = "AngleMeter"
    // accessed via getAngle
    private var angle = 0

    //////////////////
    // sensor listener
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val orientationDegrees = FloatArray(3)

    //////////////////
    // get sensor manager service
    fun create(activity: FragmentActivity) {
        sensorManager = activity?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    //////////////////
    // start sensor listener
    fun start() {
        // get updates from the accelerometer and magnetometer at a constant rate.
        // https://developer.android.com/guide/topics/sensors/sensors_position
        // https://developer.android.com/reference/android/hardware/SensorEventListener
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL * 1000 * 1000,    // 3 secs
                SensorManager.SENSOR_DELAY_UI * 1000 * 1000       // 2 secs
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(
                this,
                magneticField,
                SensorManager.SENSOR_DELAY_NORMAL * 1000 * 1000,
                SensorManager.SENSOR_DELAY_UI * 1000 * 1000
            )
        }

    }
    //////////////////
    // stop sensor listener
    fun stop() {
        // unregister listener
        sensorManager.unregisterListener(this)
    }
    //////////////////
    // get camera angle
    fun getAngle(): Int {
        return angle
    }
    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            Log.d(TAG, "onSensorChanged accelerometerReading->" + accelerometerReading.contentToString())
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            Log.d(TAG, "onSensorChanged magnetometerReading->" + magnetometerReading.contentToString())
        }

        this.angle = updateOrientationAngles()
    }

    // Compute the three orientation angles based on the most recent readings from
    // the device's accelerometer and magnetometer.
    private fun updateOrientationAngles(): Int {
        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        // "rotationMatrix" now has up-to-date information.

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // "orientationAngles" now has up-to-date information.

        //Log.d(TAG, "updateOrientationAngles rotationMatrix->" + rotationMatrix.contentToString())
        //Log.d(TAG, "updateOrientationAngles orientationAngles->" + orientationAngles.contentToString())

        // convert orientation angles (radians) to degrees
        for ((index, angle) in orientationAngles.withIndex()) {
            // orientationDegrees[index] = orientationAngles[index] * 57.2958f
            orientationDegrees[index] = (orientationAngles[index] * (180/ PI)).toFloat()
        }
        //Log.d(TAG, "updateOrientationAngles orientationDegrees->" + orientationDegrees.contentToString())

        // shift orientation degrees to camera angle - 0=parallel to earth, 90=perpendicular to earth
        val angle = 90 + orientationDegrees[1].toInt()   // adjust neg angles to 0(parallel to earth) to 90(flat, straight up)
        //senseViewModel.editCameraAngle.value = 90 + orientationDegrees[1].toInt()   // adjust neg angles to 0(parallel to earth) to 90(flat, straight up)
        //senseViewModel.editCameraAngle.value = (orientationDegrees[1].toInt() * -1)
        Log.d(TAG, "updateOrientationAngles CameraAngle->$angle")
        return angle
    }
    // mandatory unused override
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
//        updateOrientationAngles()
    }
}
///////////////////////////////////////////////////////////////////////////////
