
package com.preciosai.photo_capture_plugin

import android.content.Context
import android.hardware.*
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.core.Core
import org.opencv.core.Point


class HorizonLeveler(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    var rollDegrees: Float = 0f
        private set

    fun start() {
        sensorManager.registerListener(
            this,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {

            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val roll = orientation[2] // radians
            rollDegrees = Math.toDegrees(roll.toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}


fun fixHorizon(src: Mat, rollDegrees: Float): Mat {
    val center = Point(src.cols() / 2.0, src.rows() / 2.0)
    val rotMat = Imgproc.getRotationMatrix2D(center, -rollDegrees.toDouble(), 1.0)

    val dst = Mat()
    Imgproc.warpAffine(
        src,
        dst,
        rotMat,
        src.size(),
        Imgproc.INTER_LINEAR,
        Core.BORDER_REPLICATE
    )
    return dst
}
