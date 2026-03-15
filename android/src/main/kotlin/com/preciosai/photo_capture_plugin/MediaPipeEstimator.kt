package com.preciosai.photo_capture_plugin

import android.content.Context
import android.graphics.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Size
import kotlinx.coroutines.*

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult


class MediaPipeEstimator(
    private val context: Context,
    private val useGpu: Boolean = true,
    override var confidenceThreshold: Float = 0.65f,
    override var iouThreshold: Float = 0.45f,
) : BasePredictor() {

    companion object {
        private const val TAG = "MediaPipeEstimator"
        private const val numItemsThreshold = 10
        private const val KEYPOINTS_NUMBER = 33
    }

    private lateinit var modelPath: MappedByteBuffer
    private lateinit var poseLandmarker: PoseLandmarker

    private val rotateMatrix = Matrix()
    private var reusableBitmap: Bitmap? = null
    private var reusableCanvas: Canvas? = null

    private var mpImage: MPImage? = null
    private var mpImageBitmap: Bitmap? = null

    private fun initLandmarkerWithDelegate(delegate: Delegate) {
        val baseOptions = BaseOptions.builder()
            .setModelAssetBuffer(modelPath)
            .setDelegate(delegate)
            .build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)

        poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
    }

    private suspend fun initLandmarker() {
        val prefs = context.getSharedPreferences("mediapipe_prefs", Context.MODE_PRIVATE)
        val gpuWorked = prefs.getBoolean("gpu_works_on_this_device", true)
        val delegate = if (gpuWorked) Delegate.GPU else Delegate.CPU
        Log.d(TAG, "GPU works on this device: $gpuWorked, delegate $delegate")

        val success = withTimeoutOrNull(6_000L) {
            try {
                withContext(Dispatchers.IO) {
                    initLandmarkerWithDelegate(delegate)
                }
                true
            } catch (e: Exception) {
                null
            }
        }

        if (success == null && delegate == Delegate.GPU) {
            // Запомнить что GPU не работает на этом устройстве
            prefs.edit().putBoolean("gpu_works_on_this_device", false).apply()
            Log.w(TAG, "GPU failed, saving preference and switching to CPU")

            withContext(Dispatchers.IO) {
                initLandmarkerWithDelegate(Delegate.CPU)
            }
        }
    }

    init {
        Log.d(TAG, "MediaPipe init")
        modelPath = loadModelFromFlutterAsset(context, "assets/models/pose_landmarker_full.task")
        runBlocking {
            initLandmarker()
        }
    }

    override fun close() {
        poseLandmarker.close()
    }

    private fun loadModelFromFlutterAsset(context: Context, assetName: String): MappedByteBuffer {
        val file = CameraViewUtils.assetFileFromFlutter(context, assetName)

        // Используем блок use для автоматического закрытия RandomAccessFile
        return java.io.RandomAccessFile(file, "r").use { randomAccessFile ->
            val fileChannel = randomAccessFile.channel
            fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }

    override fun predict(
        bitmap: Bitmap,
        origWidth: Int,
        origHeight: Int,
        rotateForCamera: Boolean,
        isLandscape: Boolean,
        isFrontCamera: Boolean,
        rotationDegrees: Int
    ): InstanceObj {
        t0 = System.nanoTime()
        val timestampMs = System.nanoTime() / 1_000_000

        val processedBitmap = if (rotateForCamera || isFrontCamera) {

            if (reusableBitmap == null ||
                reusableBitmap!!.width != origWidth ||
                reusableBitmap!!.height != origHeight
            ) {
                reusableBitmap = Bitmap.createBitmap(origWidth, origHeight, Bitmap.Config.ARGB_8888)
                reusableCanvas = Canvas(reusableBitmap!!)
            }

            rotateMatrix.reset()
            rotateMatrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            if (rotateForCamera) {
                rotateMatrix.postRotate(rotationDegrees.toFloat())
            }
            if (isFrontCamera) {
                rotateMatrix.postScale(-1f, 1f, 0f, 0f)
            }
            rotateMatrix.postTranslate(origWidth / 2f, origHeight / 2f)

            reusableCanvas!!.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
            reusableCanvas!!.drawBitmap(bitmap, rotateMatrix, null)

            reusableBitmap!!

        } else {
            bitmap
        }

        if (mpImage == null || mpImageBitmap !== processedBitmap) {
            mpImage = BitmapImageBuilder(processedBitmap).build()
            mpImageBitmap = processedBitmap
        }

        val result = poseLandmarker.detectForVideo(mpImage!!, timestampMs)

        val prediction = mapToPredictionObj(
            result,
            processedBitmap.width.toFloat(),
            processedBitmap.height.toFloat(),
        )

        val infTime = (System.nanoTime() - t0) / 1_000_000.0
        Log.d(TAG, "MediaPipe inference time: $infTime ms")
        Log.d(TAG, "MediaPipe t3 diff: ${(System.nanoTime() - t3) / 1_000_000.0} ms, t2: $t2")
        Log.d(TAG, "MediaPipe fps: ${if (t4 > 0) (1.0 / t4) else 0.0}")
        updateTiming()

        if (prediction == null) {
            return InstanceObj(
                imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
                objects = mutableListOf(),
                speed = t2,
                fps = if (t4 > 0) (1.0 / t4) else 0.0,
            )
        }

        return InstanceObj(
            imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
            objects = listOf(prediction),
            speed = t2,
            fps = if (t4 > 0) (1.0 / t4) else 0.0,
        )
    }

    private fun mapToPredictionObj(
        result: PoseLandmarkerResult,
        imageWidth: Float,
        imageHeight: Float
    ): PredictionObj? {
        val landmarks = result.landmarks().firstOrNull() ?: return null

        val xynList = mutableListOf<Pair<Float, Float>>()
        val xyList = mutableListOf<Pair<Float, Float>>()
        // val znList = mutableListOf<Float>()
        val scoresList = mutableListOf<Float>()

        var minXn = Float.MAX_VALUE
        var minYn = Float.MAX_VALUE
        var maxXn = Float.MIN_VALUE
        var maxYn = Float.MIN_VALUE

        var totalScore = 0f
        var validPointsCount = 0

        for (landmark in landmarks) {
            val xn = landmark.x()
            val yn = landmark.y()
            // val zn = landmark.z()
            val visibility = landmark.visibility().orElse(0f)
            val presence = landmark.presence().orElse(0f)

            val isOutOfBounds = xn < 0f || xn > 1f || yn < 0f || yn > 1f
            val finalScore = if (presence < 0.7f || isOutOfBounds) 0f else visibility

            xynList.add(Pair(xn, yn))
            xyList.add(Pair(xn * imageWidth, yn * imageHeight))
            // znList.add(zn)
            scoresList.add(finalScore)

            if (finalScore > 0f) {
                totalScore += finalScore
                validPointsCount++

                if (finalScore > 0.2f) {
                    if (xn < minXn) minXn = xn
                    if (yn < minYn) minYn = yn
                    if (xn > maxXn) maxXn = xn
                    if (yn > maxYn) maxYn = yn
                }
            }
        }

        if (validPointsCount == 0 || minXn == Float.MAX_VALUE) {
            return null
        }

        val skeletonWidthN = maxXn - minXn
        val skeletonHeightN = maxYn - minYn

        val paddingXn = skeletonWidthN * 0.15f
        val paddingYn = skeletonHeightN * 0.1f

        minXn = (minXn - paddingXn).coerceIn(0f, 1f)
        minYn = (minYn - paddingYn).coerceIn(0f, 1f)
        maxXn = (maxXn + paddingXn).coerceIn(0f, 1f)
        maxYn = (maxYn + paddingYn).coerceIn(0f, 1f)

        val xywhn = RectF(minXn, minYn, maxXn, maxYn)

        val xywh = RectF(
            minXn * imageWidth,
            minYn * imageHeight,
            maxXn * imageWidth,
            maxYn * imageHeight
        )

        val avgScore = totalScore / validPointsCount

        val bbox = BBox(
            clsIndex = 0,
            label = "person",
            score = avgScore,
            xywh = xywh,
            xywhn = xywhn
        )

        val keypoints = Keypoints(
            xyn = xynList,
            xy = xyList,
            // zn = znList,
            scores = scoresList
        )

        return PredictionObj(
            bbox = bbox,
            keypoints = keypoints,
            label = "person",
            score = avgScore
        )
    }
}
