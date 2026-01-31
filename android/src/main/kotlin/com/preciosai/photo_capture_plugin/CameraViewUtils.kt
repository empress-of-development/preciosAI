package com.preciosai.photo_capture_plugin

import android.media.MediaPlayer
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import org.opencv.android.Utils
import org.opencv.core.Mat
import android.graphics.BitmapFactory
import android.content.Context
import android.util.Log
import java.io.File
import androidx.camera.core.CameraSelector
import android.view.View
import android.graphics.Bitmap
import java.io.FileOutputStream
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import java.io.ByteArrayOutputStream
import android.graphics.Rect
import android.graphics.*
import androidx.camera.core.ImageProxy


data class ZoomState(
    var currentZoomRatio: Float = 1.0f,
    var minZoomRatio: Float = 1.0f,
    var maxZoomRatio: Float = 10.0f,
)

data class ThrottleState(
    var lastInferenceTime: Long = 0,
    val maxFPS: Int? = 10,
    var targetFrameInterval: Long? = null, // in nanoseconds
    val throttleIntervalMs: Int? = 100,
    var throttleInterval: Long? = null // in nanoseconds
)


object CameraViewUtils {
    private const val TAG = "CameraViewUtils"

    fun bytesToMat(bytes: ByteArray): Mat {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Cannot decode bitmap")

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    fun loadLabels(context: Context, modelPath: String): List<String> {
        val loadedLabels = YOLOFileUtils.loadLabelsFromAppendedZip(context, modelPath)
        if (loadedLabels != null) {
            Log.d(TAG, "Labels loaded from model metadata: ${loadedLabels.size} classes")
            return loadedLabels
        }

        Log.d(TAG, "Using COCO classes as fallback")
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

    fun playAssetSound(context: Context, audioPlayer: MediaPlayer, assetPath: String) {
        try {
            val assetManager = context.assets
            val input = assetManager.open(assetPath)
            Log.d(TAG, "SOUND assetPath")

            // создаём временный файл
            val tempFile = File.createTempFile("sound_", ".wav", context.cacheDir)
            tempFile.outputStream().use { input.copyTo(it) }

            audioPlayer.setDataSource(tempFile.absolutePath)
            audioPlayer.prepare()
            audioPlayer.start()

        } catch (e: Exception) {
            Log.e("Sound", "Error: ${e.message}")
        }
    }

    fun extensionName(mode: Int) = when (mode) {
        ExtensionMode.HDR -> "HDR"
        ExtensionMode.NIGHT -> "NIGHT"
        ExtensionMode.FACE_RETOUCH -> "FACE_RETOUCH"
        ExtensionMode.BOKEH -> "BOKEH"
        else -> "UNKNOWN($mode)"
    }

    fun extensionModeCheck(extensionsManager: ExtensionsManager) {
        Log.d(TAG, "ExtensionMode check")

        for (mode in listOf(
            ExtensionMode.HDR,
            ExtensionMode.NIGHT,
            ExtensionMode.FACE_RETOUCH,
            ExtensionMode.BOKEH
        )) {
            Log.d(
                TAG,
                "Extension ${extensionName(mode)} available = ${
                    extensionsManager!!.isExtensionAvailable(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        mode
                    )
                }"
            )
        }
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.getExternalFilesDir(null), "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }

        return file
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String) : Any {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$filename")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        Log.d(TAG, "res image uri: $uri")

        val new_bitmap = Bitmap.createBitmap(bitmap, 0, (bitmap.height - bitmap.width * 4 / 3) / 2, bitmap.width, bitmap.width * 4 / 3)

        uri?.let {
            resolver.openOutputStream(it).use { outStream ->
                new_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream!!)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        // Encode bitmap to JPEG bytes
        val byteStream = java.io.ByteArrayOutputStream()
        new_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteStream)
        val bytes = byteStream.toByteArray()

        return bytes
    }

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y
        yBuffer.get(nv21, 0, ySize)

        // NV21 = Y + V + U
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            100,
            out
        )

        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    fun assetFileFromFlutter(context: Context, assetName: String): File {
        val file = File(context.filesDir, File(assetName).name)
        if (file.exists() && file.length() > 0) return file
        val flutterAssetPath = "flutter_assets/$assetName"

        context.assets.open(flutterAssetPath).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file
    }
}
