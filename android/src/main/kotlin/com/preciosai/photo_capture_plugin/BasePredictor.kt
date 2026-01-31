package com.preciosai.photo_capture_plugin

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.opencv.core.Size


abstract class BasePredictor {
    protected lateinit var interpreter: Interpreter
    protected fun isInterpreterInitialized() = this::interpreter.isInitialized
    protected lateinit var modelInputSize: Pair<Int, Int>

    protected var t0: Long = 0L
    protected var t2: Double = 0.0
    protected var t3: Long = System.nanoTime()
    protected var t4: Double = 0.0

    open lateinit var inputSize: Size
    open lateinit var labels: List<String>
    open var confidenceThreshold: Float = 0.25f
    open var iouThreshold: Float = 0.4f
    open var isFrontCamera: Boolean = false
    open var isLandscape: Boolean = false

    protected fun updateTiming() {
        val now = System.nanoTime()
        val dt = (now - t0) / 1e9
        t2 = 0.05 * dt + 0.95 * t2
        t4 = 0.05 * ((now - t3) / 1e9) + 0.95 * t4
        t3 = now
    }

    abstract fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean = false, isLandscape: Boolean = false): InstanceObj
}
