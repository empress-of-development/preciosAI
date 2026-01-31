package com.preciosai.photo_capture_plugin

import android.content.Context
import android.graphics.*
import android.util.Log
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Size
import java.io.File
import java.io.FileOutputStream


class YOLOPoseEstimator(
    context: Context,
    modelPath: String,
    override var labels: List<String>,
    private val useGpu: Boolean = true,
    override var confidenceThreshold: Float = 0.65f,
    override var iouThreshold: Float = 0.45f,
    val customOptions: Interpreter.Options? = null
) : BasePredictor() {

    companion object {
        private const val TAG = "YOLOPoseEstimator"
        private const val numItemsThreshold = 10

        // xywh(4) + conf(1) + keypoints(17*3=51) = 56
        private const val OUTPUT_FEATURES = 56
        private const val KEYPOINTS_COUNT = 17
    }

    private val interpreterOptions = (customOptions ?: Interpreter.Options()).apply {
        if (customOptions == null) {
            setNumThreads(Runtime.getRuntime().availableProcessors())
        }
        
        if (useGpu) {
            try {
                addDelegate(GpuDelegate())
                Log.d(TAG, "GPU delegate is used.")
            } catch (e: Exception) {
                Log.e(TAG, "GPU delegate error: ${e.message}")
            }
        }
    }

    private lateinit var imageProcessorCameraPortrait: ImageProcessor
    private lateinit var imageProcessorCameraPortraitFront: ImageProcessor
    private lateinit var imageProcessorCameraLandscape: ImageProcessor
    private lateinit var imageProcessorSingleImage: ImageProcessor
    
    // Reuse ByteBuffer for input to reduce allocations
    private lateinit var inputBuffer: ByteBuffer
    // Reuse output arrays to reduce allocations
    private lateinit var outputArray: Array<Array<FloatArray>>
    
    private var batchSize = 0
    private var numAnchors = 0

    init {
        val modelBuffer = loadModelFromFlutterAsset(context, "assets/models/$modelPath")

        var loadedLabels = YOLOFileUtils.loadLabelsFromAppendedZip(context, modelPath)
        var labelsWereLoaded = loadedLabels != null

        if (loadedLabels != null) {
            this.labels = loadedLabels!! // Use labels from appended ZIP
            Log.i(TAG, "Labels successfully loaded from appended ZIP.")
        } else {
            Log.w(TAG, "Could not load labels from appended ZIP, trying FlatBuffers metadata...")
            // Try FlatBuffers as a fallback
            if (loadLabelsFromFlatbuffers(modelBuffer)) {
                labelsWereLoaded = true
                Log.i(TAG, "Labels successfully loaded from FlatBuffers metadata.")
            }
        }

        if (!labelsWereLoaded) {
            Log.w(TAG, "No embedded labels found from appended ZIP or FlatBuffers. Using labels passed via constructor (if any) or an empty list.")
            if (this.labels.isEmpty()) {
                Log.w(TAG, "Warning: No labels loaded and no labels provided via constructor. Detections might lack class names.")
            }
        }

        interpreter = Interpreter(modelBuffer, interpreterOptions)
        interpreter.allocateTensors()
        Log.d(TAG, "TFLite model loaded and tensors allocated")

        val inputShape = interpreter.getInputTensor(0).shape()
        val inHeight = inputShape[1]
        val inWidth = inputShape[2]
        modelInputSize = Pair(inWidth, inHeight)
        
        val outputShape = interpreter.getOutputTensor(0).shape()
        batchSize = outputShape[0]           // 1
        val outFeatures = outputShape[1]     // 56
        numAnchors = outputShape[2]          // 2100 etc.
        require(outFeatures == OUTPUT_FEATURES) {
            "Unexpected output feature size. Expected=$OUTPUT_FEATURES, Actual=$outFeatures"
        }
        
        outputArray = Array(batchSize) {
            Array(outFeatures) { FloatArray(numAnchors) }
        }
        
        val inputBytes = 1 * inHeight * inWidth * 3 * 4 // FLOAT32 is 4 bytes
        inputBuffer = ByteBuffer.allocateDirect(inputBytes).apply {
            order(java.nio.ByteOrder.nativeOrder())
        }

        // For camera feed in portrait mode (with rotation)
        imageProcessorCameraPortrait = ImageProcessor.Builder()
            .add(Rot90Op(3))  // 270-degree rotation for back camera
            .add(ResizeOp(inHeight, inWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()
            
        // For front camera in portrait mode (90-degree rotation to the right)
        imageProcessorCameraPortraitFront = ImageProcessor.Builder()
            .add(Rot90Op(1))  // 90-degree rotation to the right for front camera
            .add(ResizeOp(inHeight, inWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()
            
        // For camera feed in landscape mode (no rotation)
        imageProcessorCameraLandscape = ImageProcessor.Builder()
            .add(ResizeOp(inHeight, inWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()
            
        // For single images (no rotation needed)
        imageProcessorSingleImage = ImageProcessor.Builder()
            .add(ResizeOp(inHeight, inWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .add(CastOp(DataType.FLOAT32))
            .build()
    }

    private fun loadModelFromFlutterAsset(context: Context, assetName: String): java.nio.MappedByteBuffer {
        val file = CameraViewUtils.assetFileFromFlutter(context, assetName)
        val fileChannel = java.io.RandomAccessFile(file, "r").channel
        val startOffset = 0L
        val declaredLength = file.length()
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun predict(bitmap: Bitmap, origWidth: Int, origHeight: Int, rotateForCamera: Boolean, isLandscape: Boolean): InstanceObj {
        t0 = System.nanoTime()
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        val processedImage = if (rotateForCamera) {
            if (isLandscape) {
                imageProcessorCameraLandscape.process(tensorImage)
            } else {
                if (isFrontCamera) {
                    imageProcessorCameraPortraitFront.process(tensorImage)
                } else {
                    imageProcessorCameraPortrait.process(tensorImage)
                }
            }
        } else {
            // No rotation for single image
            imageProcessorSingleImage.process(tensorImage)
        }
        
        inputBuffer.clear()
        inputBuffer.put(processedImage.buffer)
        inputBuffer.rewind()

        interpreter.run(inputBuffer, outputArray)
        // Update processing time measurement
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
            speed = t2,   // Measurement values in milliseconds etc. depend on BasePredictor implementation
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

        val predictions = mutableListOf<PredictionObj>()

        val (modelW, modelH) = modelInputSize
        val scaleX = origWidth.toFloat() / modelW
        val scaleY = origHeight.toFloat() / modelH

        for (j in 0 until numAnchors) {
            val rawX = features[0][j]
            val rawY = features[1][j]
            val rawW = features[2][j]
            val rawH = features[3][j]
            val conf = features[4][j]

            if (conf < confidenceThreshold) continue

            val xScaled = rawX * modelW
            val yScaled = rawY * modelH
            val wScaled = rawW * modelW
            val hScaled = rawH * modelH

            val left   = xScaled - wScaled / 2f
            val top    = yScaled - hScaled / 2f
            val right  = xScaled + wScaled / 2f
            val bottom = yScaled + hScaled / 2f

            val normBox = RectF(left / modelW, top / modelH, right / modelW, bottom / modelH)

            val rectF = RectF(
                left   * scaleX,
                top    * scaleY,
                right  * scaleX,
                bottom * scaleY
            )

            val kpArray = mutableListOf<Pair<Float, Float>>()
            val kpConfArray = mutableListOf<Float>()
            for (k in 0 until KEYPOINTS_COUNT) {
                val rawKx = features[5 + k * 3][j]
                val rawKy = features[5 + k * 3 + 1][j]
                val kpC   = features[5 + k * 3 + 2][j]

                // Check if values are already in pixel coordinates (>1) or normalized (0-1)
                val isNormalized = rawKx <= 1.0f && rawKy <= 1.0f
                
                val finalKx: Float
                val finalKy: Float
                
                if (isNormalized) {
                    val kxScaled = rawKx * modelW
                    val kyScaled = rawKy * modelH
                    finalKx = kxScaled * scaleX
                    finalKy = kyScaled * scaleY
                } else {
                    finalKx = rawKx * scaleX
                    finalKy = rawKy * scaleY
                }

                kpArray.add(finalKx to finalKy)
                kpConfArray.add(kpC)
            }

            val xynList = kpArray.map { (fx, fy) ->
                (fx / origWidth) to (fy / origHeight)
            }
            
            predictions.add(
                PredictionObj(
                    bbox = BBox(
                        clsIndex = 0,
                        label = "person",
                        score = conf,
                        xywh = rectF,
                        xywhn = normBox
                    ),
                    keypoints = Keypoints(
                        xyn = xynList,
                        xy = kpArray,
                        scores = kpConfArray
                    ),
                    label = "person",
                    score = conf
                )
            )
        }

        val finalDetections = nmsPredictionObj(predictions, iouThreshold)
        return finalDetections
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
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        val unionArea = a.width() * a.height() + b.width() * b.height() - interArea
        return if (unionArea <= 0f) 0f else (interArea / unionArea)
    }

    private fun loadLabelsFromFlatbuffers(buf: MappedByteBuffer): Boolean = try {
        val extractor = MetadataExtractor(buf)
        val files = extractor.associatedFileNames
        if (!files.isNullOrEmpty()) {
            for (fileName in files) {
                Log.d(TAG, "Found associated file: $fileName")
                extractor.getAssociatedFile(fileName)?.use { stream ->
                    val fileString = String(stream.readBytes(), Charsets.UTF_8)
                    Log.d(TAG, "Associated file contents:\n$fileString")

                    val yaml = Yaml()
                    @Suppress("UNCHECKED_CAST")
                    val data = yaml.load<Map<String, Any>>(fileString)
                    if (data != null && data.containsKey("names")) {
                        val namesMap = data["names"] as? Map<Int, String>
                        if (namesMap != null) {
                            labels = namesMap.values.toList()
                            Log.d(TAG, "Loaded labels from metadata: $labels")
                            return true
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "No associated files found in the metadata.")
        }
        false
    } catch (e: Exception) {
        Log.e(TAG, "Failed to extract metadata: ${e.message}")
        false
    }
}
