package com.preciosai.photo_capture_plugin

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.opencv.core.Size


abstract class BasePredictor {
    protected var t0: Long = 0L
    protected var t2: Double = 0.0
    protected var t3: Long = System.nanoTime()
    protected var t4: Double = 0.0

    open var confidenceThreshold: Float = 0.25f
    open var iouThreshold: Float = 0.4f
    open var isFrontCamera: Boolean = false
    open var isLandscape: Boolean = false

    protected fun updateTiming() {
        val now = System.nanoTime()

        // first call / not initialized yet
        if (t3 == 0L) {
            t3 = now
            if (t0 == 0L) t0 = now
            return
        }

        val dtFromT0 = (now - t0) * NS_TO_S
        val dtBetweenCalls = (now - t3) * NS_TO_S

        t2 = ema(dtFromT0, t2, ALPHA)
        t4 = ema(dtBetweenCalls, t4, ALPHA)

        t3 = now
    }

    private fun ema(x: Double, prev: Double, alpha: Double): Double =
        alpha * x + (1.0 - alpha) * prev

    private companion object {
        const val ALPHA = 0.05
        const val NS_TO_S = 1e-9
    }

    abstract fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean = false, isLandscape: Boolean = false): InstanceObj

    abstract fun close()
}
