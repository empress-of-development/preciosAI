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


const val MODEL_TYPE: String = "MMPoseEstimator" // "MMPoseEstimator" "YOLOPoseEstimator" "MediaPipeEstimator"


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

    private val autoZoom = AutoZoom()
    public var refDetectionResult: InstanceObj? = null
    private val iouThreshold = 0.65
    private val comparePoseUseSmoothing = false
    private val poseEvaluator = PoseEvaluator(alpha = 0.2f)
    private var poseComparisonMode = "cosine" // simple OKS PDJ cosine

    public var comparePoseThreshold = 0.7
    public var visualizationMode = "skeleton"

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

    //private var modelLoadCallback: ((Boolean) -> Unit)? = null

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

        // Ensure overlay is visually above the preview container ???
        //overlayView.elevation = 100f
        //overlayView.translationZ = 100f
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
            extensionsManager = ExtensionsManager
                .getInstanceAsync(context, cameraProvider)
                .get()

            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            val isPreviewStabilizationSupported =
                Preview.getPreviewCapabilities(cameraProvider.getCameraInfo(cameraSelector))
                    .isStabilizationSupported
            Log.d(TAG, "isPreviewStabilizationSupported $isPreviewStabilizationSupported")

            previewInit = try {
                Preview.Builder()
                    .setPreviewStabilizationEnabled(true)
                    .build()
            } catch (e: Exception) {
                Log.d(TAG, "isPreviewStabilizationSupported false!!!")

                Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
            }
            previewInit?.setSurfaceProvider(previewView.surfaceProvider)

            // ---------- ImageAnalysis ----------
            imageAnalysisInit = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                //.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetResolution(android.util.Size(480, 640))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            cameraExecutor = Executors.newSingleThreadExecutor()
            imageAnalysisInit!!.setAnalyzer(cameraExecutor!!) { imageProxy ->
                onFrame(imageProxy)
                imageProxy.close()
            }

            // ---------- ImageCapture ----------
            imageCaptureInit = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            var cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
            var capabilities = ImageCapture.getImageCaptureCapabilities(cameraInfo)
            val characs = Camera2CameraInfo.from(cameraInfo)

            val map = characs.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG)
            Log.d("HDR", "JPEG sizes = ${sizes?.joinToString()}")
            Log.d("HDR", "formats=${capabilities.supportedOutputFormats}, cameraInfo.sensorLandscapeRatio ${cameraInfo}")
            Log.d("HDR", "formats=${capabilities.supportedOutputFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG)}")

            // ---------- Bind ----------
            val owner = lifecycleOwner
            if (owner == null) {
                Log.e(TAG, "No LifecycleOwner available")
                return@addListener
            }

            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(
                    owner,
                    cameraSelector,
                    previewInit,
                    imageAnalysisInit,
                    //imageCaptureInit
                )

                camera?.let { cam ->
                    val zoomStateValue = cam.cameraInfo.zoomState.value
                    zoomState.minZoomRatio = zoomStateValue?.minZoomRatio ?: 1f
                    zoomState.maxZoomRatio = zoomStateValue?.maxZoomRatio ?: 10f
                    zoomState.currentZoomRatio = zoomStateValue?.zoomRatio ?: 1f
                    onZoomChanged?.invoke(zoomState.currentZoomRatio)
                }

                Log.d(TAG, "Camera setup completed successfully minZoomRatio ${zoomState.minZoomRatio}, maxZoomRatio ${zoomState.maxZoomRatio}")

            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
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
            imageAnalysisForResult,
            imageCaptureForResult
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
        camera!!.cameraControl.setZoomRatio(zoomState.currentZoomRatio)
        delay(100)

        camera!!.cameraControl.setExposureCompensationIndex(0)
        delay(100)

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
        val resImages: MutableList<ByteArray> = mutableListOf<ByteArray>()

        val imageCapture = imageCaptureForResult
            ?: throw IllegalStateException("ImageCapture not initialized")


        /*
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "PRS_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PreciosAI")
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()
         */

        var hdrBytes: ByteArray? = null
        if (useHDR && !hdrInProgress) {
            captureRequested = true
            Log.d(TAG, "hdrInProgress repeat")
            hdrBytes = resultImageShoot.HDR(camera!!, imageCapture, imageAnalysisForResult, cameraExecutor!!, context.contentResolver)
            hdrInProgress = false
        }
        if (hdrBytes != null) {
            Log.d(TAG, "add hdrBytes")
            resImages.add(hdrBytes)
        }

        /*
        // Save ordinary image without additional processing
        val resImg = suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                outputOptions,
                cameraExecutor!!,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        try {
                            val uri = output.savedUri
                                ?: return cont.resumeWithException(
                                    IllegalStateException("SavedUri is null")
                                )

                            context.contentResolver.openInputStream(uri).use { inputStream ->
                                val bytes = inputStream!!.readBytes()
                                val mat = CameraViewUtils.bytesToMat(bytes)
                                val beigeMat = resultImageShoot.stylizeBeigeLook(mat)
                                resultImageShoot.saveMatAsJpeg(beigeMat, context.contentResolver)
                                cont.resume(bytes)
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
         */

        val resImg = suspendCancellableCoroutine<ByteArray> { cont ->
            imageCapture.takePicture(
                cameraExecutor!!,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val mat = CameraViewUtils.imageProxyToRotatedMat(image)
                            image.close()

                            // TODO update image postprocessing
                            // val beigeMat = resultImageShoot.stylizeBeigeLook(mat)
                            val jpegBytes = CameraViewUtils.matToJpegBytes(mat)

                            resultImageShoot.saveMatAsJpeg(mat, context.contentResolver)

                            mat.release()
                            // beigeMat.release()
                            cont.resume(jpegBytes)

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
        return resImages
    }

    private fun requestCapture() {
        Log.d(TAG, "requestCapture")
        if (captureRequested) return

        captureRequested = true

        mainScope.launch {
            try {
                Log.d(TAG, "startCaptureModeWithExtensions")
                val bytes = startCaptureModeWithExtensions()

                val serialized: List<Map<String, Any>> = bytes.map { byteArray ->
                    mapOf("data" to byteArray)
                }
                sendResultPhotoPath(serialized)
            } catch (e: Throwable) {
                Log.e(TAG, "Capture failed", e)
            } finally {
                //captureRequested = false
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
                var tInit = System.nanoTime()
                val result = if (isLandscape) {
                    p.predict(reusableBitmap!!, imageProxy.width, imageProxy.height, rotateForCamera = true, isLandscape = isLandscape)
                } else {
                    // In portrait mode, keep the original behavior (h, w)
                    p.predict(reusableBitmap!!, imageProxy.height, imageProxy.width, rotateForCamera = true, isLandscape = isLandscape)
                }
                Log.d(TAG, "Prediction time ${(System.nanoTime() - tInit) / 1_000_000.0}")

                if (result.objects.isEmpty() || refDetectionResult!!.objects.isEmpty()) {
                    val state = OverlayState(
                        result = result,
                        refDetectionResult = refDetectionResult,
                        isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
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
                var poseComparisonDetails: Map<String, Float?>? = null

                if (autoZoom.refZoomData == null) {
                    // по референсному кадру расчитываем параметры для автозума только один раз
                    // TODO берется первый объект
                    autoZoom.getRefZoomData(refPredictionObj)
                    Log.d(TAG, "autoZoom.refZoomData: ${autoZoom.refZoomData}")
                }

                if (refDetectionResult != null && autoZoom.refZoomData != null && currentPredictionObj.keypoints != null) {
                    // if (autoZoom.zoomDurationCount < autoZoom.zoomDurationThreshold) {
                    // zoomLevel == null, если камера слишком близко или
                    // уровень зума относительно уже существующего приближения
                    var zoomLevel = autoZoom.getZoomLevel(currentPredictionObj)
                    Log.d(TAG, "zoomLevel: $zoomLevel, currentZoomRatio: ${zoomState.currentZoomRatio}")

                    if (zoomLevel != null) {
                        // если
                        if (autoZoom.tooCloseInd > 0) {
                            // если был сигнал отойти назад, ждем несколько кадров для проверки
                            autoZoom.zoomDurationCount = 0
                            autoZoom.tooCloseInd = autoZoom.tooCloseInd - 1
                        } else {
                            if (abs(zoomLevel - 1f) > 0.1) {
                                setZoomLevel(zoomState.currentZoomRatio + zoomLevel - 1f)
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
                    Log.d(TAG, "autoZoom.stage, ${autoZoom.stage}, autoZoom.zoomDurationCount, ${autoZoom.zoomDurationCount}")

                    if (autoZoom.zoomDurationCount == autoZoom.zoomDurationThreshold && autoZoom.stage == "zoom") {
                        Log.d(TAG, "Turn on the audio, place_model.wav")
                        CameraViewUtils.playAssetSound(context, audioPlayer, "flutter_assets/assets/audio/place_model.wav")
                        autoZoom.stage = "location"
                    }

                    if (autoZoom.stage == "location") {
                        var resIou = iou(refPredictionObj.bbox.xywhn, currentPredictionObj.bbox.xywhn)
                        Log.d(TAG, "autoZoom.stage, ${autoZoom.stage}, autoZoom.locationDurationCount, ${autoZoom.zoomDurationCount}")

                        if (resIou > iouThreshold) {
                            autoZoom.locationDurationCount++
                        }
                        if (autoZoom.locationDurationCount == autoZoom.locationDurationThreshold) {
                            pulseRectAnimation.setAnimationVisible(false)
                            autoZoom.stage = "kps_comparison"
                        }
                    }

                    if (autoZoom.stage == "kps_comparison") {
                        if (pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(false)

                        var resIou = iou(refPredictionObj.bbox.xywhn, currentPredictionObj.bbox.xywhn)
                        if (resIou < iouThreshold) {
                            autoZoom.kpsComparisonDurationCount = 0
                        } else {
                            var compareRes: Float?
                            if (comparePoseUseSmoothing) {
                                val poseComparisonResult = poseEvaluator.compareAndSmooth(refPredictionObj, currentPredictionObj)
                                compareRes = poseComparisonResult.overallScore
                                poseComparisonDetails = poseComparisonResult.details
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
                                    if (poseComparisonDetails != null) {
                                        val location_score = poseComparisonDetails["location"] ?: 0f
                                        if (location_score < 0.7 && !pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(
                                            true
                                        )
                                        if (location_score > 0.7 && pulseRectAnimation.state.visible) pulseRectAnimation.setAnimationVisible(
                                            false
                                        )
                                    }
                                    Log.d(TAG, "poseComparisonDetails $poseComparisonDetails")
                                }
                            }

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
                                                progressBarLottieAnimation.loadAndPlay(autoPlay = true, speed = 3f)
                                            }
                                            requestCapture()
                                            /*
                                            val res_bitmap = previewView.bitmap
                                            bytes = CameraViewUtils.saveBitmapToGallery(
                                                context,
                                                res_bitmap!!,
                                                "photo_${System.currentTimeMillis()}.jpg"
                                            )
                                             */
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
                    isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                    captureRequested = captureRequested,
                    showPulseRect = pulseRectAnimation.state.visible,
                    rectAlpha = pulseRectAnimation.state.animationOffset.toInt(),
                    showBackArrow = backArrowAnimation.state.visible,
                    arrowAnimationOffset = backArrowAnimation.state.animationOffset,
                    poseComparisonDetails = poseComparisonDetails,
                    visualizationMode = visualizationMode,
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
