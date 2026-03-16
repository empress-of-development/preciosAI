package com.preciosai.photo_capture_plugin

import android.Manifest
import android.app.Activity
import android.media.MediaPlayer
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.AspectRatioStrategy

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.BinaryMessenger
import android.os.Handler
import android.os.Looper
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.Observer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.Channel

import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.photo.Photo
import org.opencv.android.Utils
import org.opencv.core.Core
import android.util.Size as SizeUtil
import org.opencv.core.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCharacteristics
import android.view.OrientationEventListener
import android.graphics.RectF


// TODO костыль!
const val MODEL_TYPE: String = "MediaPipeEstimator" // "MMPoseEstimator" "YOLOPoseEstimator" "MediaPipeEstimator"


object PredictorManager {
    @Volatile var isLoaded = false
    var predictor: BasePredictor? = null
    var modelType: String = MODEL_TYPE

    // Создаем scope для фоновых задач, чтобы не плодить потоки
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initPredictor(context: Context, onLoaded: (Boolean) -> Unit) {
        if (isLoaded && predictor != null) {
            Log.d("PredictorManager", "Predictor already loaded")
            onLoaded(true)
            return
        }

        // Запускаем корутину в фоновом потоке
        scope.launch {
            try {
                Log.d("PredictorManager", "Starting model load: $modelType")

                val newPredictor = when (modelType) {
                    "MMPoseEstimator" -> MMPoseEstimator(context)
                    "YOLOPoseEstimator" -> YOLOPoseEstimator(
                        context,
                        "yolo11n-pose.tflite",
                        CameraViewUtils.loadLabels(),
                        useGpu = true
                    )
                    else -> MediaPipeEstimator(context)
                }

                // Возвращаемся в главный поток для безопасного обновления переменных
                withContext(Dispatchers.Main) {
                    predictor = newPredictor
                    isLoaded = true
                    Log.d("PredictorManager", "Model loaded successfully: $predictor")
                    onLoaded(true) // Сообщаем CameraView, что можно забирать модель
                }

            } catch (e: Exception) {
                Log.e("PredictorManager", "Error loading model", e)
                withContext(Dispatchers.Main) {
                    isLoaded = false
                    predictor = null
                    onLoaded(false) // Сообщаем об ошибке
                }
            }
        }
    }

    fun releaseModel() {
        predictor?.close()
        predictor = null
        isLoaded = false
    }
}

class CameraView @JvmOverloads constructor(
    context: Context,
    private val methodChannel: MethodChannel?,
    private val messenger: BinaryMessenger,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "CameraView"

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // Basics
    private var lifecycleOwner: LifecycleOwner? = null
    private val modelType: String = MODEL_TYPE
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    public val autoZoom = AutoZoom()
    public var refDetectionResult: InstanceObj? = null
    private val iouThreshold = 0.5
    private val comparePoseUseSmoothing = true
    private val poseComparator = PoseComparator(
        mode = if (modelType == "MediaPipeEstimator") "MediaPipe" else "COCO",
        alpha = 0.4f,
    )
    private var poseComparisonMode = "cosine" // simple OKS PDJ cosine

    public var comparePoseThreshold = 0.7
    public var visualizationMode = "skeleton+capsules"

    // Throttling variables for performance control
    private var throttleState = ThrottleState()

    // Audio player
    private var audioPlayer: MediaPlayer = MediaPlayer();

    // Animations
    private var backArrowAnimation = AnimationController(
        state = AnimationState(duration = 800L, limits = Pair(0f, 1f)),
        requestInvalidate = { invalidate() },
        post = { action -> post(action) }
    )
    private var pulseRectAnimation = AnimationController(
        state = AnimationState(duration = 1000L, limits = Pair(50f, 100f)),
        requestInvalidate = { invalidate() },
        post = { action -> post(action) }
    )
    private val progressBarLottieAnimation = LottieAnimation(context, "assets/animation/progress_bar.json")
    private var reusableBitmap: Bitmap? = null

    private val previewView: PreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
    }

    // The overlay for drawing
    private val overlayView: OverlayView = OverlayView(
        context,
        if (modelType == "MediaPipeEstimator") "MediaPipe" else "COCO"
    )

    private var predictor: BasePredictor? = null
    // Getter for external access to predictor
    val predictorInstance: BasePredictor?
        get() = predictor

    // Camera config
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var camera: Camera? = null

    // Camera settings for analysis
    private var cameraExecutor: ExecutorService? = null
    private var imageAnalysisInit: ImageAnalysis? = null
    private lateinit var imageCaptureInit: ImageCapture
    private lateinit var previewInit: Preview
    private lateinit var extensionsManager: ExtensionsManager
    private lateinit var cameraSelector: CameraSelector
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var zoomState = ZoomState()
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    var onZoomChanged: ((Float) -> Unit)? = null

    // Result photo capture
    private lateinit var imageCaptureForResult: ImageCapture
    private lateinit var imageAnalysisForResult: ImageAnalysis
    private var hdrInProgress = false
    private var useHDR = false
    private var sendResultPhotoInd = false
    private var captureRequested = false
    private var resultImageShoot = ResultImageShoot()

    val methodChannelAdd = MethodChannel(messenger, "photo_capture_channel_add")
    private var orientationEventListener: OrientationEventListener? = null
    private var rotationUpdated = false

    init {
        removeAllViews()

        val previewContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        previewContainer.addView(previewView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        addView(previewContainer)

        addView(overlayView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))
        previewContainer.elevation = 1f

        // Zoom anf focus
        scaleGestureDetector = ScaleGestureDetector(context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val camera = camera ?: return false
                    val newZoom = zoomState.currentZoomRatio * detector.scaleFactor
                    val clamped = newZoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)

                    camera.cameraControl.setZoomRatio(clamped)
                    zoomState.currentZoomRatio = clamped

                    onZoomChanged?.invoke(zoomState.currentZoomRatio)
                    return true
                }
            }
        )

        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }

        val offsetDp = 200
        val offsetPx = offsetDp * context.resources.displayMetrics.density
        progressBarLottieAnimation.lottieView.translationY = offsetPx
        previewContainer.addView(progressBarLottieAnimation.lottieView)

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Failed to init")
        }
    }

    fun setZoomLevel(zoomLevel: Float) {
        camera?.let { cam: Camera ->
            val clampedZoomRatio = zoomLevel.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)

            cam.cameraControl.setZoomRatio(clampedZoomRatio)
            zoomState.currentZoomRatio = clampedZoomRatio

            onZoomChanged?.invoke(zoomState.currentZoomRatio)
        }
    }

    fun setPreviewZoomLevel(zoomLevel: Float) {
        camera?.let { cam: Camera ->
            val clampedZoomRatio = zoomLevel.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)

            previewView.scaleX = clampedZoomRatio
            previewView.scaleY = clampedZoomRatio

            zoomState.currentZoomRatio = clampedZoomRatio

            onZoomChanged?.invoke(zoomState.currentZoomRatio)
        }
    }

    fun setModel(callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "setModel start")

        PredictorManager.initPredictor(context) { success ->
            if (success) {
                this.predictor = PredictorManager.predictor
                Log.d(TAG, "setModel end, predictor ${this.predictor}")
                sendSignalToDart()
                callback?.invoke(true)
            } else {
                Log.e(TAG, "Failed to load model in PredictorManager")
                callback?.invoke(false)
            }
        }
    }

    private fun sendSignalToDart() {
        val channel = methodChannelAdd ?: return
        Log.d(TAG, "sendModelReadySignal")
        Handler(Looper.getMainLooper()).post {
            methodChannelAdd.invokeMethod("onModelReady", mapOf("status" to "success"))
        }
    }

    fun onLifecycleOwnerAvailable(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
        owner.lifecycle.addObserver(this)

        // If camera was requested but couldn't start due to missing lifecycle owner, try again
        if (allPermissionsGranted()) {
            startCamera()
        }
        Log.d(TAG, "LifecycleOwner set: ${owner.javaClass.simpleName}")
    }

    fun initCamera() {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            val activity = context as? Activity ?: return
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(context, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startCamera() {
        Log.d(TAG, "Starting camera...")

        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            extensionsManager = ExtensionsManager.getInstanceAsync(context, cameraProvider).get()

            cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            val extensionMode = if (useHDR) ExtensionMode.HDR else ExtensionMode.NONE
            val isExtensionAvailable = useHDR && extensionsManager.isExtensionAvailable(cameraSelector, extensionMode)

            val finalSelector = if (isExtensionAvailable) {
                extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, extensionMode)
            } else {
                cameraSelector
            }

            // Unified Aspect Ratio Strategy for all use cases to prevent "No supported surface combination"
            val ratioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

            previewInit = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratioStrategy).build())
                .build()
            previewInit?.setSurfaceProvider(previewView.surfaceProvider)

            val analysisSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratioStrategy)
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(480, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
                .build()

            imageAnalysisInit = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(analysisSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            if (cameraExecutor == null) cameraExecutor = Executors.newSingleThreadExecutor()
            imageAnalysisInit!!.setAnalyzer(cameraExecutor!!) { imageProxy ->
                onFrame(imageProxy)
                imageProxy.close()
            }

            val captureSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratioStrategy)
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            imageCaptureInit = ImageCapture.Builder()
                .setResolutionSelector(captureSelector)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
                .build()


            setupOrientationListener(context, previewInit!!, imageAnalysisInit!!)

            val owner = lifecycleOwner ?: return@addListener
            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(
                    owner, finalSelector, previewInit, imageAnalysisInit, imageCaptureInit
                )
                Log.d(TAG, "Triple binding successful. " +
                        "Preview: ${previewInit.resolutionInfo?.resolution}, " +
                        "Analysis: ${imageAnalysisInit?.resolutionInfo?.resolution}, " +
                        "Capture: ${imageCaptureInit.resolutionInfo?.resolution}")
            } catch (e: Exception) {
                Log.w(TAG, "Triple binding failed, falling back to Preview + Analysis", e)
                try {
                    camera = cameraProvider.bindToLifecycle(owner, finalSelector, previewInit, imageAnalysisInit)
                } catch (e2: Exception) {
                    Log.e(TAG, "Critical: Camera binding failed entirely", e2)
                }
            }

            camera?.cameraControl?.setZoomRatio(zoomState.currentZoomRatio)
            camera?.let { cam ->
                val zs = cam.cameraInfo.zoomState.value
                zoomState.minZoomRatio = zs?.minZoomRatio ?: 1f
                zoomState.maxZoomRatio = zs?.maxZoomRatio ?: 10f
            }
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun startCaptureModeWithExtensions(): List<ByteArray> {

        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()

        val preview = Preview.Builder()
            //.setTargetResolution(SizeUtil(1920, 1080))
            .build()


        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    SizeUtil(2048, 1024),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                )
            )
            .build()
        imageAnalysisForResult = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageCaptureForResult = ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val extensionMode = ExtensionMode.HDR // или NIGHT и т.п.
        Log.d(TAG, "startCaptureModeWithExtensions AUTO")
        val selector =
            if (extensionsManager!!.isExtensionAvailable(cameraSelector, extensionMode)) {
                Log.d(TAG, "Extension $extensionMode is available")
                extensionsManager!!
                    .getExtensionEnabledCameraSelector(cameraSelector, extensionMode)
            } else {
                Log.w(TAG, "Extension $extensionMode not available, using base selector")
                cameraSelector
            }

        cameraProvider.bindToLifecycle(
            lifecycleOwner!!,
            selector,
            preview,
            //imageAnalysisForResult,
            imageCaptureForResult
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
        camera!!.cameraControl.setZoomRatio(zoomState.currentZoomRatio)
        delay(350)

        //camera!!.cameraControl.setExposureCompensationIndex(0)
        //delay(1000)

        // ждем пока PreviewView реально не начнёт показывать видеопоток с камеры
        suspendUntilPreviewStreaming()

        CameraViewUtils.extensionModeCheck(extensionsManager)

        Log.d(TAG, "takePhotoWithExtensions")

        return takePhotoWithExtensions()

    }

    private suspend fun suspendUntilPreviewStreaming() =
        suspendCancellableCoroutine<Unit> { cont ->

            val observer = object : Observer<PreviewView.StreamState> {
                override fun onChanged(state: PreviewView.StreamState) {
                    Log.d(TAG, "suspendUntilPreviewStreaming PreviewView.StreamState $state")
                    if (state == PreviewView.StreamState.STREAMING) {
                        previewView.previewStreamState.removeObserver(this)
                        cont.resume(Unit)
                    }
                }
            }

            previewView.previewStreamState.observe(lifecycleOwner!!, observer)

            cont.invokeOnCancellation {
                previewView.previewStreamState.removeObserver(observer)
            }
        }


    private suspend fun takePhotoWithExtensions(): List<ByteArray> {
        val resImages = mutableListOf<ByteArray>()

        val imageCapture = imageCaptureInit ?: throw IllegalStateException("ImageCapture not initialized")
        val imageAnalysis = imageAnalysisInit

        if (useHDR && !hdrInProgress) {
            captureRequested = true
            val hdrBytes = resultImageShoot.HDR(
                camera!!, imageCapture, imageAnalysis!!, cameraExecutor!!, context.contentResolver
            )
            hdrBytes?.let { resImages.add(it) }
            hdrInProgress = false
        }

        try {
            val resImg = suspendCancellableCoroutine<ByteArray> { cont ->
                imageCapture.takePicture(
                    cameraExecutor!!,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val mat = CameraViewUtils.imageProxyToRotatedMat(image)
                                image.close()

                                val isFront = predictor?.isFrontCamera ?: false

                                val jpegBytes = resultImageShoot.saveMatAndGetItems(
                                    mat = mat,
                                    resolver = context.contentResolver,
                                    isFrontCamera = isFront,
                                    quality = 95
                                )

                                mat.release()

                                if (jpegBytes != null) {
                                    cont.resume(jpegBytes)
                                } else {
                                    cont.resumeWithException(RuntimeException("Failed to process Mat"))
                                }

                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            captureRequested = false
                            cont.resumeWithException(exception)
                        }
                    }
                )
            }
            resImages.add(resImg)
        } catch (e: Exception) {
            Log.e(TAG, "Capture error: ${e.message}")
        }

        return resImages
    }

    private fun requestCapture() {
        Log.d(TAG, "requestCapture")
        if (captureRequested) return
        captureRequested = true

        mainScope.launch {
            try {
                // Trigger capture without unbinding or delays
                val bytes = takePhotoWithExtensions()

                val serialized: List<Map<String, Any>> = bytes.map { mapOf("data" to it) }
                sendResultPhotoPath(serialized)
            } catch (e: Throwable) {
                Log.e(TAG, "Capture failed", e)
                captureRequested = false
            }
        }
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    // endregion

    // Lifecycle methods from DefaultLifecycleObserver
    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle onStart")
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "Lifecycle onStop")
        // Camera will be automatically stopped by CameraX when lifecycle stops
    }

    private fun sendResultPhotoPath(bytes: Any) {
        val channel = methodChannel ?: return
        Log.d(TAG, "sendResultPhotoPath")
        Handler(Looper.getMainLooper()).post {
            methodChannel.invokeMethod("goToResult", mapOf("bytes" to bytes))
        }
    }

    private fun setupOrientationListener(context: Context, preview: Preview, imageAnalysis: ImageAnalysis) {
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270 // Reverse Landscape
                    in 135..224 -> Surface.ROTATION_180 // Reverse Portrait
                    in 225..314 -> Surface.ROTATION_90  // Landscape
                    else -> Surface.ROTATION_0          // Portrait
                }

                rotationUpdated = true

                // Обновляем целевую ротацию для UseCases
                preview.targetRotation = rotation
                imageAnalysis.targetRotation = rotation
            }
        }
        orientationEventListener?.enable()
    }

    private fun getCorrectedResolution(proxyWidth: Int, proxyHeight: Int, rotationDegrees: Int) {

        if (refDetectionResult != null) {

            var offsetLeft: Float = 0f
            var offsetTop: Float = 0f
            var ratio = proxyWidth.toFloat() / proxyHeight.toFloat()
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                // Вертикальный кадр
                ratio = proxyHeight.toFloat() / proxyWidth.toFloat()
            }

            val refRatio =
                refDetectionResult!!.imageShape.width / refDetectionResult!!.imageShape.height

            if (ratio > refRatio) {
                // добавляем место по бокам
                offsetLeft = (refDetectionResult!!.imageShape.height * (ratio - refRatio) / 2).toFloat()
                if (offsetLeft != refDetectionResult!!.offsetLeft) {
                    for ((idx, refPred) in refDetectionResult!!.objects.withIndex()) {

                        refDetectionResult!!.imageShapeCorrected = Size(
                            refDetectionResult!!.imageShape.height * ratio,
                            refDetectionResult!!.imageShape.height
                        )
                        refDetectionResult!!.objects[idx].bbox.xywhnCorrected = RectF(
                            (refDetectionResult!!.objects[idx].bbox.xywh.left + offsetLeft) / refDetectionResult!!.imageShapeCorrected!!.width.toFloat(),
                            refDetectionResult!!.objects[idx].bbox.xywhn.top,
                            (refDetectionResult!!.objects[idx].bbox.xywh.right + offsetLeft) / refDetectionResult!!.imageShapeCorrected!!.width.toFloat(),
                            refDetectionResult!!.objects[idx].bbox.xywhn.bottom,
                        )

                        val xynCorrected = mutableListOf<Pair<Float, Float>>()
                        for ((idx, point) in refDetectionResult!!.objects[idx].keypoints.xy.withIndex()) {
                            xynCorrected.add(
                                Pair(
                                    ((point.first + offsetLeft) / refDetectionResult!!.imageShapeCorrected!!.width).toFloat(),
                                    ((point.second + offsetTop) / refDetectionResult!!.imageShapeCorrected!!.height).toFloat()
                                )
                            )
                        }
                        refDetectionResult!!.objects[idx].keypoints.xynCorrected = xynCorrected
                    }
                    refDetectionResult!!.offsetLeft = offsetLeft
                    refDetectionResult!!.offsetTop = 0f
                }
            } else {
                // добавляем место сверху
                offsetTop = (refDetectionResult!!.imageShape.width.toFloat() * (refRatio - ratio)).toFloat()
                if (offsetTop != refDetectionResult!!.offsetTop) {
                    for ((idx, refPred) in refDetectionResult!!.objects.withIndex()) {

                        refDetectionResult!!.imageShapeCorrected = Size(
                            refDetectionResult!!.imageShape.width,
                            refDetectionResult!!.imageShape.width / ratio
                        )
                        refDetectionResult!!.objects[idx].bbox.xywhnCorrected = RectF(
                            refDetectionResult!!.objects[idx].bbox.xywhn.left,
                            (refDetectionResult!!.objects[idx].bbox.xywh.top + offsetTop) / refDetectionResult!!.imageShapeCorrected!!.height.toFloat(),
                            refDetectionResult!!.objects[idx].bbox.xywhn.right,
                            (refDetectionResult!!.objects[idx].bbox.xywh.bottom + offsetTop) / refDetectionResult!!.imageShapeCorrected!!.height.toFloat(),
                        )

                        val xynCorrected = mutableListOf<Pair<Float, Float>>()
                        for ((idx, point) in refDetectionResult!!.objects[idx].keypoints.xy.withIndex()) {
                            xynCorrected.add(
                                Pair(
                                    ((point.first + offsetLeft) / refDetectionResult!!.imageShapeCorrected!!.width).toFloat(),
                                    ((point.second + offsetTop) / refDetectionResult!!.imageShapeCorrected!!.height).toFloat()
                                )
                            )
                        }
                        refDetectionResult!!.objects[idx].keypoints.xynCorrected = xynCorrected

                    }
                    refDetectionResult!!.offsetTop = offsetTop
                    refDetectionResult!!.offsetLeft = 0f
                }
            }
            Log.d(TAG, "Corrected resolution: offsetTop ${refDetectionResult!!.offsetTop}, offsetLeft ${refDetectionResult!!.offsetLeft}")
        }
    }


    private fun onFrame(imageProxy: ImageProxy) {
         if (captureRequested) {
            val state = OverlayState(
                result = null,
                refDetectionResult = null,
                isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                captureRequested = captureRequested,
                showPulseRect = false,
                rectAlpha = pulseRectAnimation.state.animationOffset.toInt(),
                showBackArrow = false,
                arrowAnimationOffset = backArrowAnimation.state.animationOffset
            )
            post {
                overlayView.updateState(state)
            }
            return
        }

        /*
        val bitmap = imageProxy.toBitmap() ?: run {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
            return
        }
         */
        if (reusableBitmap == null || reusableBitmap!!.width != imageProxy.width || reusableBitmap!!.height != imageProxy.height) {
            reusableBitmap = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        }
        reusableBitmap!!.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        predictor?.let { p ->
            if (!shouldRunInference()) {
                // Log.d(TAG, "Skipping inference due to frequency control")
                return
            }

            try {
                if (refDetectionResult == null) return@let null
                // Get device orientation
                val orientation = context.resources.configuration.orientation
                val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT

                // Set camera facing information in predictor
                (p as? BasePredictor)?.isFrontCamera = isFrontCamera

                // TODO проверить!!!
                // For camera feed, we typically rotate the bitmap
                // In landscape mode, we don't rotate, so width/height should match actual bitmap dimensions
                val rotateForCamera = if (imageProxy.imageInfo.rotationDegrees != 0) true else false
                var tInit = System.nanoTime()

                getCorrectedResolution(imageProxy.width, imageProxy.height, imageProxy.imageInfo.rotationDegrees)
                val result = if (isLandscape) {
                    p.predict(reusableBitmap!!, imageProxy.width, imageProxy.height, rotateForCamera, isLandscape, isFrontCamera, imageProxy.imageInfo.rotationDegrees)
                } else {
                    // Portrait mode
                    p.predict(reusableBitmap!!, imageProxy.height, imageProxy.width, rotateForCamera, isLandscape, isFrontCamera, imageProxy.imageInfo.rotationDegrees)
                }
                // Log.d(TAG, "Prediction time ${(System.nanoTime() - tInit) / 1_000_000.0}")

                if (result.objects.isEmpty() || refDetectionResult!!.objects.isEmpty()) {
                    val state = OverlayState(
                        result = result,
                        refDetectionResult = refDetectionResult,
                        isFrontCamera = isFrontCamera,
                        captureRequested = captureRequested,
                        showPulseRect = pulseRectAnimation.state.visible,
                        rectAlpha = pulseRectAnimation.state.animationOffset.toInt(),
                        showBackArrow = backArrowAnimation.state.visible,
                        arrowAnimationOffset = backArrowAnimation.state.animationOffset
                    )
                    post {
                        overlayView.updateState(state)
                    }
                    return@let null
                }

                val refPredictionObj = refDetectionResult!!.objects[0]
                val currentPredictionObj = result.objects[0]
                var poseComparisonDetails: Map<String, Float?>? = if (poseComparator.previousResult != null) poseComparator.previousResult!!.details else null

                Log.d(TAG, "rotationUpdated $rotationUpdated")
                if (autoZoom.refZoomData == null || rotationUpdated) {
                    // по референсному кадру расчитываем параметры для автозума только один раз
                    // TODO берется первый объект
                    autoZoom.getRefZoomData(refPredictionObj)
                }

                if (refDetectionResult != null && autoZoom.refZoomData != null && currentPredictionObj.keypoints != null) {
                    // if (autoZoom.zoomDurationCount < autoZoom.zoomDurationThreshold) {
                    // zoomLevel == null, если камера слишком близко или
                    // уровень зума относительно уже существующего приближения
                    var zoomLevel = autoZoom.getZoomLevel(currentPredictionObj)
                    // Log.d(TAG, "zoomLevel: $zoomLevel, currentZoomRatio: ${zoomState.currentZoomRatio}")

                    if (zoomLevel != null) {
                        // если
                        if (autoZoom.tooCloseInd > 0) {
                            // если был сигнал отойти назад, ждем несколько кадров для проверки
                            autoZoom.zoomDurationCount = 0
                            autoZoom.tooCloseInd = autoZoom.tooCloseInd - 1
                        } else {
                            if (abs(zoomLevel - 1f) > 0.1) {
                                setZoomLevel(zoomState.currentZoomRatio + zoomLevel - 1f)
                                //setPreviewZoomLevel(zoomState.currentZoomRatio + zoomLevel - 1f)
                                Thread.sleep(250)
                            } else {
                                autoZoom.zoomDurationCount++
                            }
                            // если зум настраивается и zoomLevel != null, отключаем стрелку отойти назад
                            if (backArrowAnimation.state.visible) backArrowAnimation.setAnimationVisible(false)

                            // начинаем показываеть зону объекта
                            if (!pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(true)
                        }
                    } else {
                        // модель предположительно близко
                        // ждем несколько кадров, если все езе близко, включаем аудио и стрелку назад
                        if (autoZoom.tooCloseInd == 3) {
                            Log.d(TAG, "Turn on the audio, step_back_short.wav")
                            CameraViewUtils.playAssetSound(context, audioPlayer, "flutter_assets/assets/audio/step_back_short.wav")
                        }
                        if (autoZoom.tooCloseInd <= 3) {
                            autoZoom.tooCloseInd++
                        } else {
                            if (!backArrowAnimation.state.visible) backArrowAnimation.setAnimationVisible(true)
                            if (pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(false)
                        }
                        autoZoom.zoomDurationCount = 0
                        Log.d(TAG, "Step away, ${autoZoom.tooCloseInd}")
                    }
                    // Log.d(TAG, "autoZoom.stage, ${autoZoom.stage}, autoZoom.zoomDurationCount, ${autoZoom.zoomDurationCount}")

                    if (autoZoom.zoomDurationCount == autoZoom.zoomDurationThreshold && autoZoom.stage == "zoom") {
                        Log.d(TAG, "Turn on the audio, place_model.wav")
                        CameraViewUtils.playAssetSound(context, audioPlayer, "flutter_assets/assets/audio/place_model.wav")
                        autoZoom.stage = "location"
                    }

                    if (autoZoom.stage == "location" && !autoZoom.isPortrait) {
                        var resIou = iou(
                            if (refPredictionObj.bbox.xywhnCorrected == null)
                                refPredictionObj.bbox.xywhn else refPredictionObj.bbox.xywhnCorrected!!,
                            currentPredictionObj.bbox.xywhn
                        )
                        // Log.d(TAG, "autoZoom.stage, ${autoZoom.stage}, autoZoom.locationDurationCount, ${autoZoom.zoomDurationCount}")
                        if (resIou > iouThreshold) {
                            autoZoom.locationDurationCount++
                        }
                        if (autoZoom.locationDurationCount == autoZoom.locationDurationThreshold) {
                            pulseRectAnimation.setAnimationVisible(false)
                            autoZoom.stage = "kps_comparison"
                        }
                    }
                    if (autoZoom.isPortrait) autoZoom.stage = "kps_comparison"

                    if (autoZoom.stage == "kps_comparison") {
                        if (pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(false)
                        var resIou = 0f
                        if (!autoZoom.isPortrait) {
                            resIou = iou(
                                if (refPredictionObj.bbox.xywhnCorrected == null)
                                    refPredictionObj.bbox.xywhn else refPredictionObj.bbox.xywhnCorrected!!,
                                currentPredictionObj.bbox.xywhn
                            )

                            Log.d(TAG, "resIou, ${resIou}")

                            if (resIou < iouThreshold) {
                                autoZoom.kpsComparisonDurationCount = 0
                                if (!pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(
                                    true
                                )
                            }
                        }
                        if (autoZoom.isPortrait || resIou > iouThreshold) {
                            var compareRes: Float?
                            if (comparePoseUseSmoothing) {
                                val poseComparisonResult = poseComparator.compare(refPredictionObj, currentPredictionObj, result.imageShape)
                                compareRes = poseComparisonResult.overallScore
                                poseComparisonDetails = poseComparisonResult.details
                                // Log.d(TAG, "compareRes $compareRes, poseComparisonDetails $poseComparisonDetails")

                            } else {
                                if (poseComparisonMode != "cosine") {
                                    compareRes = comparePoses(poseComparisonMode, refPredictionObj, currentPredictionObj, result.imageShape)
                                } else {
                                    val poseComparisonResult = cosineSimilarity(
                                        refPredictionObj,
                                        currentPredictionObj,
                                        result.imageShape,
                                        mode = if (modelType == "MediaPipeEstimator") "MediaPipe" else "COCO"
                                    )
                                    compareRes = poseComparisonResult.overallScore
                                    poseComparisonDetails = poseComparisonResult.details
                                }
                            }

                            var location_score = 0f
                            if (poseComparisonDetails != null) {
                                location_score = poseComparisonDetails["location"] ?: 0f
                            }
                            if (location_score < 0.7 && !pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(
                                true
                            )
                            if (location_score > 0.7 && pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(
                                false
                            )


                            if (compareRes != null && compareRes > comparePoseThreshold) {
                                autoZoom.kpsComparisonDurationCount++
                            } else {
                                autoZoom.kpsComparisonDurationCount = 0
                            }
                            if (autoZoom.kpsComparisonDurationCount == autoZoom.kpsComparisonDurationThreshold && !sendResultPhotoInd) {
                                sendResultPhotoInd = true
                                var bytes: Any;
                                Log.d(TAG, "switchToCaptureMode")

                                ContextCompat.getMainExecutor(context).execute {
                                    mainScope.launch {
                                        try {
                                            previewView.post {
                                                progressBarLottieAnimation.loadAndPlay(autoPlay = true, speed = 6f)
                                            }

                                            requestCapture()
                                        } catch (e: Throwable) {
                                            Log.e(TAG, "Capture failed", e)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val state = OverlayState(
                    result = result,
                    refDetectionResult = refDetectionResult,
                    isFrontCamera = isFrontCamera,
                    captureRequested = captureRequested,
                    showPulseRect = pulseRectAnimation.state.visible,
                    rectAlpha = pulseRectAnimation.state.animationOffset.toInt(),
                    showBackArrow = backArrowAnimation.state.visible,
                    arrowAnimationOffset = backArrowAnimation.state.animationOffset,
                    poseComparisonDetails = poseComparisonDetails,
                    visualizationMode = visualizationMode,
                    isPortrait = autoZoom.isPortrait
                )
                post {
                    overlayView.updateState(state)
                }
                throttleState.lastInferenceTime = System.nanoTime()

            } catch (e: Exception) {
                Log.e(TAG, "Error during prediction", e)
            }
        }
    }

    // OverlayView нужен для отделения отрисовки от камеры/preview
    private inner class OverlayView @JvmOverloads constructor(context: Context, poseMode: String) : View(context) {
        private val renderer = OverlayRenderer(poseMode)
        private var state: OverlayState? = null

        init {
            // Make background transparent
            setBackgroundColor(Color.TRANSPARENT)
            // Use hardware layer for better z-order 
            setLayerType(LAYER_TYPE_HARDWARE, null)

            // Raise overlay
            elevation = 100f
            translationZ = 100f

            setWillNotDraw(false)

            // Make overlay not intercept touch events
            isClickable = false
            isFocusable = false
        }

        fun updateState(state: OverlayState) {
            this.state = state
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            state?.let {
                renderer.draw(
                    canvas = canvas,
                    width = width.toFloat(),
                    height = height.toFloat(),
                    state = it
                )
            }
        }
        override fun onTouchEvent(event: MotionEvent?): Boolean {
            // Pass through all touch events
            return false
        }
    }


    public fun setupThrottling() {
        throttleState.maxFPS?.let { maxFPS ->
            if (maxFPS > 0) {
                throttleState.targetFrameInterval = (1_000_000_000L / maxFPS) // Convert to nanoseconds
                Log.d(
                    TAG,
                    "maxFPS throttling enabled - target FPS: $maxFPS, interval: ${throttleState.targetFrameInterval!! / 1_000_000}ms"
                )
            }
        }

        throttleState.throttleIntervalMs?.let { throttleIntervalMs ->
            if (throttleIntervalMs > 0) {
                throttleState.throttleInterval = throttleIntervalMs * 1_000_000L // Convert ms to nanoseconds
                Log.d(TAG, "throttleInterval enabled - interval: ${throttleIntervalMs}ms")
            }
        }

    }

    private fun shouldRunInference(): Boolean {
        val now = System.nanoTime()

        // Check maxFPS throttling
        throttleState.targetFrameInterval?.let { interval ->
            if (now - throttleState.lastInferenceTime < interval) {
                return false
            }
        }

        // Check throttleInterval
        throttleState.throttleInterval?.let { interval ->
            if (now - throttleState.lastInferenceTime < interval) {
                return false
            }
        }

        return true
    }

    fun stop() {
        Log.d(TAG, "stop called, tearing down camera")

        if (captureRequested) {
            Log.w(TAG, "Skip stop(): capture in progress")
            return
        }
        mainScope.launch {
            while (captureRequested) {
                Log.d(TAG, "captureRequested!!!")
                delay(2000)
            }
        }

        if (::cameraProviderFuture.isInitialized) {
            val cameraProvider = cameraProviderFuture.get()
            Log.d(TAG, "Unbinding all camera use cases")
            cameraProvider.unbindAll()
        }

        imageAnalysisInit?.clearAnalyzer()
        imageAnalysisInit = null
        previewInit?.setSurfaceProvider(null)

        cameraExecutor?.let { exec ->
            Log.d(TAG, "Shutting down camera executor")
            exec.shutdown()
            if (!exec.awaitTermination(1, TimeUnit.SECONDS)) {
                Log.w(TAG, "Executor didn't shut down in time; forcing shutdown")
                exec.shutdownNow()
            }
        }
        cameraExecutor = null

        camera = null
        predictor = null

        Log.d(TAG, "CameraView stop completed successfully")

    }
}
