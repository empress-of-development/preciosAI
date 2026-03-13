package com.preciosai.photo_capture_plugin

import android.content.Context
import android.graphics.*
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Size
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.yaml.snakeyaml.Yaml


class YOLOPoseEstimator(
    context: Context,
    modelPath: String,
    var labels: List<String>,
    private val useGpu: Boolean = true,
    override var confidenceThreshold: Float = 0.65f,
    override var iouThreshold: Float = 0.45f,
) : BasePredictor() {

    companion object {
        private const val TAG = "YOLOPoseEstimator"
        private const val numItemsThreshold = 10
        private const val KEYPOINTS_NUMBER = 17
    }

    private lateinit var interpreter: Interpreter

    private lateinit var imageProcessorCameraPortrait: ImageProcessor
    private lateinit var imageProcessorCameraPortraitFront: ImageProcessor
    private lateinit var imageProcessorSingleImage: ImageProcessor
    private lateinit var modelInputSize: Pair<Int, Int>

    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputArray: Array<Array<FloatArray>>
    private var batchSize = 0
    private var numAnchors = 0

    private fun isInterpreterInitialized() = this::interpreter.isInitialized

    init {
        val modelBuffer = loadModelFromFlutterAsset(context, "assets/models/$modelPath")

        interpreter = Interpreter(
            modelBuffer,
            Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors())

                if (useGpu) {
                    try {
                        addDelegate(GpuDelegate())
                        Log.d(TAG, "GPU is used!!!")
                    } catch (e: Exception) {
                        Log.e(TAG, "GPU error: ${e.message}")
                    }
                }
            }
        )
        interpreter.allocateTensors()
        Log.d(TAG, "TFLite YOLO Pose model loaded")

        val inputShape = interpreter.getInputTensor(0).shape()
        val inputHeight = inputShape[1]
        val inputWidth = inputShape[2]
        modelInputSize = Pair(inputWidth, inputHeight)
        
        val outputShape = interpreter.getOutputTensor(0).shape()
        batchSize = outputShape[0]           // 1
        val outFeatures = outputShape[1]     // 56
        numAnchors = outputShape[2]          // 2100

        outputArray = Array(batchSize) {
            Array(outFeatures) { FloatArray(numAnchors) }
        }
        
        val inputBytes = 1 * inputHeight * inputWidth * 3 * 4 // float32 - 4
        inputBuffer = ByteBuffer.allocateDirect(inputBytes).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }

        fun createProcessor(rotationK: Int? = null) =
            ImageProcessor.Builder().apply {
                rotationK?.let { add(Rot90Op(it)) }
                add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                add(NormalizeOp(0f, 255f))
                add(CastOp(DataType.FLOAT32))
            }.build()

        // vertical image from back camera
        imageProcessorCameraPortrait = createProcessor(3)
        // vertical image from font camera
        imageProcessorCameraPortraitFront = createProcessor(1)
        // basic version - without rotation or landscape
        imageProcessorSingleImage = createProcessor()
    }

    override fun close() {
        if (isInterpreterInitialized()) {
            interpreter.close()
        }
    }

    private fun loadModelFromFlutterAsset(context: Context, assetName: String): java.nio.MappedByteBuffer {
        val file = CameraViewUtils.assetFileFromFlutter(context, assetName)
        val fileChannel = java.io.RandomAccessFile(file, "r").channel
        val startOffset = 0L
        val declaredLength = file.length()
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean, isLandscape: Boolean, isFrontCamera: Boolean, rotationDegrees: Int): InstanceObj {
        t0 = System.nanoTime()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        val processedImage = if (rotateForCamera) {
            if (isLandscape) imageProcessorSingleImage.process(tensorImage)
            else {
                if (isFrontCamera) imageProcessorCameraPortraitFront.process(tensorImage)
                else imageProcessorCameraPortrait.process(tensorImage)
            }
        } else imageProcessorSingleImage.process(tensorImage)
        
        inputBuffer.clear()
        inputBuffer.put(processedImage.buffer)
        inputBuffer.rewind()

        interpreter.run(inputBuffer, outputArray)
        updateTiming()

        val predictions = postProcessPose(
            features = outputArray[0],
            numAnchors = numAnchors,
            confidenceThreshold = confidenceThreshold,
            iouThreshold = iouThreshold,
            origWidth = origWidth,
            origHeight = origHeight
        )

        return InstanceObj(
            imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
            objects = predictions.take(numItemsThreshold),
            speed = t2,
            fps = if (t4 > 0) (1.0 / t4) else 0.0,
        )
    }

    private fun postProcessPose(
        features: Array<FloatArray>,
        numAnchors: Int,
        confidenceThreshold: Float,
        iouThreshold: Float,
        origWidth: Int,
        origHeight: Int
    ): List<PredictionObj> {

        val (modelW, modelH) = modelInputSize
        val modelWf = modelW.toFloat()
        val modelHf = modelH.toFloat()

        val scaleX = origWidth / modelWf
        val scaleY = origHeight / modelHf

        fun anchor(row: Int, j: Int) = features[row][j]

        fun toModelPx(x: Float, y: Float) = x * modelWf to y * modelHf

        fun bboxFromCenter(x: Float, y: Float, w: Float, h: Float): RectF {
            val hw = w / 2f
            val hh = h / 2f
            return RectF(x - hw, y - hh, x + hw, y + hh)
        }

        val predictions = (0 until numAnchors)
            .asSequence()
            .mapNotNull { j ->
                val conf = anchor(4, j)
                if (conf < confidenceThreshold) return@mapNotNull null

                val (cx, cy) = toModelPx(anchor(0, j), anchor(1, j))
                val bw = anchor(2, j) * modelWf
                val bh = anchor(3, j) * modelHf

                val boxModel = bboxFromCenter(cx, cy, bw, bh)

                val boxNorm = RectF(
                    boxModel.left / modelWf,
                    boxModel.top / modelHf,
                    boxModel.right / modelWf,
                    boxModel.bottom / modelHf
                )

                val boxOrig = RectF(
                    boxModel.left * scaleX,
                    boxModel.top * scaleY,
                    boxModel.right * scaleX,
                    boxModel.bottom * scaleY
                )

                val xy = ArrayList<Pair<Float, Float>>(KEYPOINTS_NUMBER)
                val scores = ArrayList<Float>(KEYPOINTS_NUMBER)
                val xyn = ArrayList<Pair<Float, Float>>(KEYPOINTS_NUMBER)

                for (k in 0 until KEYPOINTS_NUMBER) {
                    val base = 5 + k * 3

                    val rawKx = anchor(base, j)
                    val rawKy = anchor(base + 1, j)
                    val kpC = anchor(base + 2, j)

                    val (px, py) =
                        if (rawKx <= 1f && rawKy <= 1f) {
                            val mx = rawKx * modelWf
                            val my = rawKy * modelHf
                            mx * scaleX to my * scaleY
                        } else {
                            rawKx * scaleX to rawKy * scaleY
                        }

                    xy.add(px to py)
                    scores.add(kpC)
                    xyn.add(px / origWidth to py / origHeight)
                }

                PredictionObj(
                    bbox = BBox(
                        clsIndex = 0,
                        label = "person",
                        score = conf,
                        xywh = boxOrig,
                        xywhn = boxNorm
                    ),
                    keypoints = Keypoints(
                        xyn = xyn,
                        xy = xy,
                        scores = scores
                    ),
                    label = "person",
                    score = conf
                )
            }
            .toList()

        return nmsPredictionObj(predictions, iouThreshold)
    }

    private fun nmsPredictionObj(
        detections: List<PredictionObj>,
        iouThreshold: Float
    ): List<PredictionObj> {
        // val confidenceThreshold = 0.25f
        // val filteredDetections = detections.filter { it.score >= confidenceThreshold }
        
        if (detections.size <= 1) {
            return detections
        }
        
        val sorted = detections.sortedByDescending { it.score }
        val picked = mutableListOf<PredictionObj>()
        val used = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (used[i]) continue

            val d1 = sorted[i]
            picked.add(d1)

            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                val d2 = sorted[j]
                if (iou(d1.bbox.xywh, d2.bbox.xywh) > iouThreshold) {
                    used[j] = true
                }
            }
        }
        return picked
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interArea =
            (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f) *
                    (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)

        val union = a.width() * a.height() + b.width() * b.height() - interArea
        return if (union > 0f) interArea / union else 0f
    }
}
