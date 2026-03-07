package com.preciosai.photo_capture_plugin

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.ops.NormalizeOp

import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.core.graphics.scale

import android.graphics.Matrix
import android.graphics.RectF

import java.nio.FloatBuffer

import kotlin.math.min
import kotlin.math.max
import org.opencv.core.Size
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect


class MMPoseEstimator(
    private val context: Context,
    private val useGpu: Boolean = true,
    override var confidenceThreshold: Float = 0.5f,
    override var iouThreshold: Float = 0.4f,
    //val customOptions: Interpreter.Options? = null
) : BasePredictor() {

    companion object {
        private const val TAG = "MMPoseEstimator"
        init {
            System.loadLibrary("postprocess_cpp")
        }
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.25F
        private const val IOU_THRESHOLD = 0.4F
        private const val KEYPOINTS_COUNT = 133
    }

    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>
    private lateinit var inputSize: Size

    private lateinit var yoloOutSize: IntArray
    private var detWidth = 640
    private var detHeight = 640

    private lateinit var mmposeModule: Module
    private val widthMMPose: Int = 192
    private val heightMMPose: Int = 256
    private val meanMMPose = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val stdMMPose = floatArrayOf(0.229f, 0.224f, 0.225f)
    private val inputShapeMMPose = longArrayOf(1, 3, heightMMPose.toLong(), widthMMPose.toLong())
    private var outputData: FloatArray = FloatArray(399)

    private lateinit var rotatedBitmap: Bitmap
    private val tensorImageYOLO = TensorImage(DataType.FLOAT32)
    private val croppedBitmap = Bitmap.createBitmap(widthMMPose, heightMMPose, Bitmap.Config.RGB_565)
    private val pixelsBufferMMPose = IntArray(widthMMPose * heightMMPose)

    private lateinit var imagePreprocessor: ImageProcessor
    private lateinit var rawOutput: Array<Array<FloatArray>>

    private var maxBoxNorm: FloatArray? = null
    private val maxBox = IntArray(4)

    private lateinit var inputBufferMMPose: FloatBuffer
    private lateinit var inputTensorMMPose: Tensor
    private lateinit var inputEValueMMPose: EValue

    private var labelsNum: Int = 80
    private val canvas = Canvas(croppedBitmap)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val customOptions = null

    private val rotateMatrix: Matrix = Matrix().apply { postRotate(90f) }
    private var stageStartTime: Long = 0

    private var numItemsThreshold = 10
    private external fun postprocess(
        predictions: Array<FloatArray>,
        w: Int,
        h: Int,
        confidenceThreshold: Float,
        iouThreshold: Float,
        numItemsThreshold: Int,
        numClasses: Int
    ): Array<FloatArray>

    private val interpreterOptions: Interpreter.Options = (customOptions ?: Interpreter.Options()).apply {
        setNumThreads(Runtime.getRuntime().availableProcessors())
        try {
            addDelegate(GpuDelegate())
            Log.d(TAG, "GPU delegate is used.")
        } catch (e: Exception) {
            Log.e(TAG, "GPU delegate error: ${e.message}")
        }

    }

    private fun loadTFliteModelFromFile(filePath: String): java.nio.MappedByteBuffer {
        val file = java.io.File(filePath)
        val fileChannel = java.io.RandomAccessFile(file, "r").channel
        val startOffset = 0L
        val declaredLength = file.length()
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    init {
        // Load YOLO tflite model
        val modelPathYOLO = CameraViewUtils.assetFileFromFlutter(context, "assets/models/yolo11n.tflite")
        interpreter = Interpreter(modelPathYOLO, interpreterOptions)
        interpreter.allocateTensors()

        labels = loadLabels()
        labelsNum = labels.size
        Log.d(TAG, "YOLO TFlite model loaded from $modelPathYOLO")

        // YOLO preprocessing
        val inputShape = interpreter.getInputTensor(0).shape()
        val detHeight = inputShape[1]
        val detWidth = inputShape[2]
        inputSize = Size(detWidth.toDouble(), detHeight.toDouble())

        imagePreprocessor = ImageProcessor.Builder()
            //.add(ResizeOp(detHeight, detWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION
            ))
            //.add(CastOp(INPUT_IMAGE_TYPE))
            .build()

        // YOLO results
        yoloOutSize = interpreter.getOutputTensor(0).shape()
        val out1 = yoloOutSize[1] // 84
        val out2 = yoloOutSize[2] // 2100
        rawOutput = Array(1) { Array(out1) { FloatArray(out2) } }

        // Load MMPose Executorch
        val modelPath = CameraViewUtils.assetFileFromFlutter(context, "assets/models/mmpose_model_postproc_xnnpack.pte").absolutePath
        mmposeModule = Module.load(modelPath)
        Log.d(TAG, "MMPose Executorch model loaded from $modelPath")

        inputBufferMMPose = ByteBuffer
            .allocateDirect(4 * 3 * heightMMPose * widthMMPose) // 4 байта на float
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        inputTensorMMPose = Tensor.fromBlob(inputBufferMMPose, inputShapeMMPose)
        inputEValueMMPose = EValue.from(inputTensorMMPose)
    }

    private fun loadLabels(): List<String> {
        return listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog",
            "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
            "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite",
            "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich",
            "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
            "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
            "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    override fun close() {
        interpreter.close()
        // TODO fix!!!
        // mmposeModule.close()
    }

    override fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean, isLandscape: Boolean): InstanceObj {
        t0 = System.nanoTime()

        val overallStartTime = System.nanoTime()

        rotatedBitmap = if (rotateForCamera) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotateMatrix, true)
        } else {
            bitmap
        }
        tensorImageYOLO.load(rotatedBitmap.scale(detWidth, detHeight))

        stageStartTime = System.nanoTime()
        interpreter.run(imagePreprocessor.process(tensorImageYOLO).buffer, rawOutput)
        val yoloInf = (System.nanoTime() - stageStartTime) / 1_000_000.0
        Log.d(TAG, "Yolo inference time: $yoloInf ms")

        stageStartTime = System.nanoTime()
        // TODO берется один максимальный по размеру бокс, нехорошо
        maxBoxNorm = postprocess(
            rawOutput[0],
            w = yoloOutSize[2],
            h = yoloOutSize[1],
            confidenceThreshold = confidenceThreshold,
            iouThreshold = iouThreshold,
            numItemsThreshold = numItemsThreshold,
            numClasses = 1 //labelsNum
        ).maxByOrNull { box ->
            box[2] * box[3]
        }
        if (maxBoxNorm == null || maxBoxNorm!!.isEmpty()) {
            return InstanceObj(
                imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
                objects = mutableListOf<PredictionObj>(),
                speed = yoloInf,
                fps = if (t4 > 0.0) 1.0 / t4 else 0.0,
            )
        }

        val yoloPostproc = (System.nanoTime() - stageStartTime) / 1_000_000.0
        Log.d(TAG, "Yolo postproc time: $yoloPostproc ms")
        val yoloTime = (System.nanoTime() - overallStartTime) / 1_000_000.0
        Log.d(TAG, "Yolo inference time: $yoloTime ms")

        stageStartTime = System.nanoTime()

        maxBox[0] = (maxBoxNorm!![0] * rotatedBitmap.width).toInt()
        maxBox[1] = (maxBoxNorm!![1] * rotatedBitmap.height).toInt()
        maxBox[2] = (maxBoxNorm!![2] * rotatedBitmap.width).toInt()
        maxBox[3] = (maxBoxNorm!![3] * rotatedBitmap.height).toInt()

        // Create the crop for MMPose
        canvas.drawBitmap(
            rotatedBitmap,
            Rect(maxBox[0], maxBox[1], maxBox[0] + maxBox[2], maxBox[1] + maxBox[3]),
            Rect(0, 0, widthMMPose, heightMMPose),
            paint
        )
        fillTensorFromBitmap(croppedBitmap)

        System.arraycopy(
            mmposeModule.forward(inputEValueMMPose)[0].toTensor().dataAsFloatArray,
            0, outputData, 0, outputData.size
        )

        val mmposeTime = (System.nanoTime() - stageStartTime) / 1_000_000.0
        Log.d(TAG, "MMPose inference time: $mmposeTime ms")

        val totalMs = (System.nanoTime() - overallStartTime) / 1_000_000.0
        Log.d(TAG, "Predict Total time: $totalMs ms (YOLO: $yoloTime, MMPose: $mmposeTime)")

        // Log.d(TAG, "First 10 values: ${outputData.take(10)}, ${outputData.size}")

        val rect = RectF(
            max(maxBox[0], 0).toFloat(),
            max(maxBox[1], 0).toFloat(),
            min(maxBox[2] + maxBox[0], origWidth).toFloat(),
            min(maxBox[3] + maxBox[1], origHeight).toFloat(),
        )

        val bboxObj = BBox(
            clsIndex = 0,
            label = "person",
            score = maxBoxNorm!![4],
            xywh = rect,
            xywhn = RectF(
                max(maxBoxNorm!![0], 0f),
                max(maxBoxNorm!![1], 0f),
                min(maxBoxNorm!![2] + maxBoxNorm!![0], 1f),
                min(maxBoxNorm!![3] + maxBoxNorm!![1], 1f),
            )
        )

        var xn : Float
        var yn : Float
        val kps = mutableListOf<Pair<Float, Float>>()
        val kpsScores = mutableListOf<Float>()

        for (k in 0 until KEYPOINTS_COUNT) {
            // TODO Перепиши константы
            xn = outputData[k * 3] / 192 * maxBox[2] + rect.left
            yn = outputData[k * 3 + 1] / 256 * maxBox[3] + rect.top
            kps.add(xn to yn)
            kpsScores.add(outputData[k * 3 + 2])
        }

        val keypointsObj = Keypoints(
            xyn = kps.map { (fx, fy) ->
                (fx / origWidth) to (fy / origHeight)
            },
            xy = kps,
            scores = kpsScores
        )

        updateTiming()
        return InstanceObj(
            imageShape = Size(origWidth.toDouble(), origHeight.toDouble()),
            objects = listOf(
                PredictionObj(
                    bbox = bboxObj,
                    keypoints = keypointsObj,
                    label = bboxObj.label,
                    score = bboxObj.score
                )
            ),
            speed = totalMs,
            fps = if (t4 > 0.0) 1.0 / t4 else 0.0,
        )
    }

    fun fillTensorFromBitmap(bitmap: Bitmap) {
        val pixelCount = pixelsBufferMMPose.size
        bitmap.getPixels(
            pixelsBufferMMPose, 0, widthMMPose,
            0, 0, widthMMPose, heightMMPose
        )

        inputBufferMMPose.position(0)

        for (i in 0 until pixelCount) {
            val p = pixelsBufferMMPose[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            inputBufferMMPose.put(i, (r - meanMMPose[0]) / stdMMPose[0])
            inputBufferMMPose.put(i + pixelCount, (g - meanMMPose[1]) / stdMMPose[1])
            inputBufferMMPose.put(i + 2 * pixelCount, (b - meanMMPose[2]) / stdMMPose[2])
        }
        inputBufferMMPose.position(0)
    }
}
