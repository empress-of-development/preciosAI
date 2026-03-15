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
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer


data class ZoomState(
    var currentZoomRatio: Float = 1.0f,
    var minZoomRatio: Float = 1.0f,
    var maxZoomRatio: Float = 10.0f,
)

data class ThrottleState(
    var lastInferenceTime: Long = 0,
    val maxFPS: Int? = 30,
    var targetFrameInterval: Long? = null, // in nanoseconds
    val throttleIntervalMs: Int? = 20,
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

    fun loadLabels(): List<String> {
        // TODO костыль
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

        // If file already exists and is valid, don't re-copy
        if (file.exists() && file.length() > 0) {
            Log.d(TAG, "Using existing asset file: ${file.absolutePath}")
            return file
        }

        val potentialPaths = listOf(
            "flutter_assets/$assetName",
            assetName,
            "assets/flutter_assets/$assetName"
        )

        for (path in potentialPaths) {
            try {
                context.assets.open(path).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Successfully copied asset from $path to ${file.absolutePath}")
                return file
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open asset at $path: ${e.message}")
            }
        }

        throw java.io.FileNotFoundException("Asset $assetName not found in any of $potentialPaths. Check if the file is compressed in AAB.")
    }

    fun imageProxyToRotatedMat(image: ImageProxy): Mat {
        val mat = imageProxyToMat(image)

        return when (image.imageInfo.rotationDegrees) {
            90 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_CLOCKWISE) }
            180 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_180) }
            270 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_COUNTERCLOCKWISE) }
            else -> mat
        }
    }

    fun imageProxyToMat(image: ImageProxy): Mat {
        return when (image.format) {
            ImageFormat.JPEG -> jpegProxyToBgrMat(image)
            ImageFormat.YUV_420_888 -> imageProxyToBgrMat(image) // твоя быстрая версия без JPEG
            else -> error("Unsupported format: ${image.format}")
        }
    }

    private fun jpegProxyToBgrMat(image: ImageProxy): Mat {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Bitmap decode failed")

        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        bitmap.recycle()

        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()

        return bgr
    }

    fun imageProxyToBgrMat(image: ImageProxy): Mat {
        require(image.format == ImageFormat.YUV_420_888) {
            "Unsupported format: ${image.format}. Expected YUV_420_888"
        }

        val nv21 = yuv420888ToNv21(image)

        // NV21 layout: height + height/2 rows, width cols, 8UC1
        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val bgrMat = Mat()
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)

        yuvMat.release()
        return bgrMat
    }


    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val width = image.width
        val height = image.height

        val ySize = width * height
        val uvSize = width * height / 2
        val out = ByteArray(ySize + uvSize)

        // Y
        copyPlane(
            buffer = yPlane.buffer,
            rowStride = yPlane.rowStride,
            pixelStride = yPlane.pixelStride,
            width = width,
            height = height,
            out = out,
            outOffset = 0,
            outPixelStride = 1
        )

        // NV21 expects interleaved VU
        // V
        copyPlane(
            buffer = vPlane.buffer,
            rowStride = vPlane.rowStride,
            pixelStride = vPlane.pixelStride,
            width = width / 2,
            height = height / 2,
            out = out,
            outOffset = ySize,
            outPixelStride = 2
        )

        // U
        copyPlane(
            buffer = uPlane.buffer,
            rowStride = uPlane.rowStride,
            pixelStride = uPlane.pixelStride,
            width = width / 2,
            height = height / 2,
            out = out,
            outOffset = ySize + 1,
            outPixelStride = 2
        )

        return out
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int,
        outPixelStride: Int
    ) {
        buffer.rewind()
        var outputIndex = outOffset
        val row = ByteArray(rowStride)

        for (rowIndex in 0 until height) {
            val rowStart = rowIndex * rowStride
            buffer.position(rowStart)
            buffer.get(row, 0, rowStride)

            if (pixelStride == 1 && outPixelStride == 1) {
                // быстрый путь (обычно Y)
                System.arraycopy(row, 0, out, outputIndex, width)
                outputIndex += width
            } else {
                // общий путь (обычно U/V)
                var col = 0
                var inputIndex = 0
                while (col < width) {
                    out[outputIndex] = row[inputIndex]
                    outputIndex += outPixelStride
                    inputIndex += pixelStride
                    col++
                }
            }
        }
    }

    fun matToJpegBytes(
        mat: Mat,
        jpegQuality: Int = 95
    ): ByteArray {
        require(!mat.empty()) { "Mat is empty" }

        // приводим Mat к RGBA
        val rgba = Mat()
        when (mat.channels()) {
            4 -> mat.copyTo(rgba)
            3 -> Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
            1 -> Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_GRAY2RGBA)
            else -> error("Unsupported Mat channels: ${mat.channels()}")
        }

        // Mat -> Bitmap
        val bitmap = Bitmap.createBitmap(
            rgba.cols(),
            rgba.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()

        // Bitmap -> JPEG bytes
        val out = ByteArrayOutputStream()
        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            jpegQuality.coerceIn(0, 100),
            out
        )
        bitmap.recycle()

        return out.toByteArray()
    }
}
