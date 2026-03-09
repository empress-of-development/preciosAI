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

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
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

    init {
        Log.d(TAG, "MediaPipe init")

        modelPath = loadModelFromFlutterAsset(context, "assets/models/pose_landmarker_full.task")

        try {
            initLandmarkerWithDelegate(Delegate.GPU)
            Log.d(TAG, "MediaPipe pose model loaded on GPU")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to run on GPU: ${e.message}. Switch to CPU...")

            try {
                initLandmarkerWithDelegate(Delegate.CPU)
                Log.d(TAG, "MediaPipe pose model loaded on CPU")
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to run on GPU and CPU: ${ex.message}")
            }
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

    override fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean, isLandscape: Boolean): InstanceObj {
        t0 = System.nanoTime()
        val timestampMs = System.nanoTime() / 1_000_000

        val rotatedBitmap = if (rotateForCamera) {
            val newWidth = bitmap.height
            val newHeight = bitmap.width

            if (reusableBitmap == null || reusableBitmap!!.width != origWidth || reusableBitmap!!.height != origHeight) {
                reusableBitmap = Bitmap.createBitmap(origWidth, origHeight, Bitmap.Config.ARGB_8888)
                reusableCanvas = Canvas(reusableBitmap!!)
            }

            rotateMatrix.reset()
            rotateMatrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
            rotateMatrix.postRotate(90f)
            rotateMatrix.postTranslate(newWidth / 2f, newHeight / 2f)

            reusableCanvas!!.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
            reusableCanvas!!.drawBitmap(bitmap, rotateMatrix, null)

            reusableBitmap!!
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val result = poseLandmarker.detectForVideo(mpImage, timestampMs)

        Log.d(TAG, "MediaPipe t3 diff: ${(System.nanoTime() - t3) / 1_000_000.0} ms, t2: $t2")
        updateTiming()

        val prediction = mapToPredictionObj(result, rotatedBitmap.width.toFloat(), rotatedBitmap.height.toFloat())
        val infTime = (System.nanoTime() - t0) / 1_000_000.0
        Log.d(TAG, "MediaPipe inference time: $infTime ms")
        Log.d(TAG, "MediaPipe fps: ${if (t4 > 0) (1.0 / t4) else 0.0}")

        if (prediction == null) {
            return InstanceObj(
                imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
                objects = mutableListOf<PredictionObj>(),
                speed = t2,
                fps = if (t4 > 0) (1.0 / t4) else 0.0,
            )
        }

        return InstanceObj(
            imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
            objects = listOf(prediction!!),
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
