package com.preciosai.photo_capture_plugin

import org.opencv.core.Size
import org.opencv.core.Mat
import androidx.camera.core.*
import kotlinx.coroutines.channels.Channel
import android.util.Log
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import org.opencv.core.CvType
import org.opencv.core.Core
import org.opencv.core.Scalar
import org.opencv.photo.Photo
import android.graphics.BitmapFactory
import kotlinx.coroutines.delay
import androidx.camera.core.ImageAnalysis
import java.util.concurrent.ExecutorService
import androidx.camera.core.Camera
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.concurrent.futures.await
import kotlin.coroutines.resumeWithException
import android.content.ContentResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream



class ResultImageShoot {
    public var HDRMode = "takePhoto" // (imageAnalysis, takePhoto)

    // Пока только multi-exposure HDR capture, который нормально работает только на штативе
    // TODO сделать нормальный
    private var hdrInProgress = false

    //val evs = listOf(-15, -7, 7, 15)
    val evs = listOf(-6, 3, 10)
    private var HDRFramesNumber = evs.size
    private var frameBuffer = ArrayDeque<Mat>(HDRFramesNumber)
    private val frameQueue = Channel<Pair<Int, ImageProxy>>(capacity = HDRFramesNumber)

    private var currentEv: Int? = null

    companion object {
        private const val TAG = "ImageFilter"
    }

    suspend fun HDR(camera: Camera, imageCapture: ImageCapture, imageAnalysis: ImageAnalysis, cameraExecutor: ExecutorService, resolver: ContentResolver): ByteArray? {
        Log.d(TAG, "HDR call")

        if (HDRMode == "imageAnalysis") {
            captureHdrFramesAnalysis(camera, imageAnalysis, cameraExecutor)
        } else {
            captureHdrFrames(camera, imageCapture, cameraExecutor, resolver) // takePhoto!
        }
        return triggerHdrCapture(resolver)
    }

    suspend fun captureHdrFramesAnalysis(camera: Camera, imageAnalysis: ImageAnalysis, cameraExecutor: ExecutorService) {
        // Using of ImageAnalysis

        imageAnalysis.setAnalyzer(cameraExecutor) { image ->
            if (currentEv == null) {
                image.close()
                return@setAnalyzer
            }
            val rotationDegrees = image.imageInfo.rotationDegrees
            Log.d(TAG, "frameQueue.trySend, ev $currentEv, rotationDegrees $rotationDegrees")
            val result = frameQueue.trySend(currentEv!! to image)
            if (result.isFailure) {
                image.close()
            }
            currentEv = null
        }

        for (ev in evs) {
            Log.d("HDR", "captureHdrFramesAnalysis start $ev ${System.currentTimeMillis()}")

            camera.cameraControl
                .setExposureCompensationIndex(ev)
                .await()

            currentEv = ev

            val (usedEv, image) = frameQueue.receive()
            val mat = rotateMatIfNeeded(imageProxyToMat(image), image.imageInfo.rotationDegrees)
            image.close()

            frameBuffer.add(mat)
            Log.d("HDR", "captureNextHdrFrame time ${System.currentTimeMillis()}")
        }

        camera.cameraControl
            .setExposureCompensationIndex(0)
            .await()
        currentEv = null
    }

    private fun rotateMatIfNeeded(src: Mat, rotationDegrees: Int): Mat {
        val dst = Mat()
        when (rotationDegrees) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(dst)
        }
        return dst
    }

    fun imageProxyToMat(image: ImageProxy): Mat {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuv = Mat(
            image.height + image.height / 2,
            image.width,
            CvType.CV_8UC1
        )
        yuv.put(0, 0, nv21)

        val rgb = Mat()
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21)

        yuv.release()
        return rgb
    }

    suspend fun captureHdrFrames(camera: Camera, imageCapture: ImageCapture, cameraExecutor: ExecutorService, resolver: ContentResolver) {
        // Using takePhoto
        for (ev in evs) {
            camera.cameraControl.setExposureCompensationIndex(ev)
            delay(200)

            val mat = captureOne(imageCapture, cameraExecutor)
            Log.d(TAG, "ev = $ev")

            //saveMatAsJpeg(mat, resolver)
            frameBuffer.add(mat)
        }

        camera.cameraControl.setExposureCompensationIndex(0)
    }

    suspend fun captureOne(imageCapture: ImageCapture, cameraExecutor: ExecutorService): Mat =
        suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val mat = rotateMatIfNeeded(jpegImageProxyToMat(image), image.imageInfo.rotationDegrees)
                        image.close()
                        cont.resume(mat) {}
                    }
                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    /*
    private fun triggerHdrCapture(cameraExecutor: ExecutorService, resolver: ContentResolver): ByteArray? {
        Log.d(TAG, "triggerHdrCapture, size ${frameBuffer.size}")

        if (hdrInProgress || frameBuffer.size < HDRFramesNumber) return null
        hdrInProgress = true
        val frames = frameBuffer.toList()

        var hdrBytes: ByteArray? = null
        cameraExecutor.execute {
            Log.d(TAG, "HDR execute")

            val hdr: Mat
            Log.d("HDR", "processHdrDebevec time ${System.currentTimeMillis()}")

            hdr = processHdrDebevec(frames)
            Log.d("HDR", "processHdrDebevec time ${System.currentTimeMillis()}")

            //if (HDRMode == "imageAnalysis") hdr = processHdrDebevec(frames)
            //else hdr = processHdr(frames)
            saveMatAsJpeg(hdr, resolver)

            frames.forEach { it.release() }

            val bitmap = Bitmap.createBitmap(
                hdr.cols(),
                hdr.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(hdr, bitmap)
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            hdrBytes = outputStream.toByteArray()
            hdr.release()

            hdrInProgress = false
        }
        return hdrBytes
    }

     */

    suspend fun triggerHdrCapture(
        resolver: ContentResolver
    ): ByteArray? = withContext(Dispatchers.Default) {

        if (hdrInProgress || frameBuffer.size < HDRFramesNumber) return@withContext null
        hdrInProgress = true

        try {
            val frames = frameBuffer.toList()
            val hdr = processHdrDebevec(frames)

            saveMatAsJpeg(hdr, resolver)

            val bitmap = Bitmap.createBitmap(
                hdr.cols(),
                hdr.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(hdr, bitmap)

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)

            hdr.release()
            frames.forEach { it.release() }

            stream.toByteArray()
        } finally {
            hdrInProgress = false
        }
    }

    fun saveMatAsJpeg(mat: Mat, resolver: ContentResolver) {
        val bitmap = Bitmap.createBitmap(
            mat.cols(),
            mat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(mat, bitmap)

        val filename = "HDR_${System.currentTimeMillis()}.jpg"
        Log.d(TAG, "saveMatAsJpeg $filename")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HDR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        //val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return

        resolver.openOutputStream(uri)?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    fun jpegImageProxyToMat(image: ImageProxy): Mat {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size
        )

        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)

        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()

        bitmap.recycle()

        return rgb
    }

    fun stylizeBeigeLook(src: Mat): Mat {
        val result = src.clone()

        // Тональная кривая (S-curve)
        val lut = Mat(1, 256, CvType.CV_8UC1)
        for (i in 0..255) {
            val x = i / 255.0
            // S-curve: слегка поднимаем тени, приглушаем светлые
            val y = (0.8 * Math.pow(x, 0.9) + 0.2 * Math.pow(x, 1.1)).coerceIn(0.0,1.0)
            lut.put(0, i, (y*255.0))
        }
        val channels = ArrayList<Mat>()
        Core.split(result, channels)
        for (i in 0..2) {
            Core.LUT(channels[i], lut, channels[i])
        }
        Core.merge(channels, result)
        channels.forEach { it.release() }
        lut.release()

        // Приглушение кислотных цветов
        val lab = Mat()
        Imgproc.cvtColor(result, lab, Imgproc.COLOR_RGB2Lab)
        val labCh = ArrayList<Mat>(3)
        Core.split(lab, labCh)
        Core.multiply(labCh[1], Scalar(1.05), labCh[1]) // A канал: меньше красного/зелёного
        Core.multiply(labCh[2], Scalar(1.0), labCh[2]) // B канал: слегка теплее
        Core.merge(labCh, lab)
        Imgproc.cvtColor(lab, result, Imgproc.COLOR_Lab2RGB)
        lab.release()
        labCh.forEach { it.release() }

        // Снижение насыщенности
        val hsv = Mat()
        Imgproc.cvtColor(result, hsv, Imgproc.COLOR_RGB2HSV)
        val hsvCh = ArrayList<Mat>(3)
        Core.split(hsv, hsvCh)
        Core.multiply(hsvCh[1], Scalar(0.96), hsvCh[1]) // уменьшение S на 8%
        Core.merge(hsvCh, hsv)
        Imgproc.cvtColor(hsv, result, Imgproc.COLOR_HSV2RGB)
        hsv.release()
        hsvCh.forEach { it.release() }

        // Локальный контраст (CLAHE)
        val lab2 = Mat()
        Imgproc.cvtColor(result, lab2, Imgproc.COLOR_RGB2Lab)
        val labCh2 = ArrayList<Mat>(3)
        Core.split(lab2, labCh2)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0,8.0))
        clahe.apply(labCh2[0], labCh2[0])
        Core.merge(labCh2, lab2)
        Imgproc.cvtColor(lab2, result, Imgproc.COLOR_Lab2RGB)
        lab2.release()
        labCh2.forEach { it.release() }

        return result
    }


    fun processHdrDebevec(
        frames: List<Mat>,
    ): Mat {
        val T0 = 0.0333f // базовое время при EV=0

        //val evsTmp = listOf(-3, -1, 1, 3)
        val evsTmp = evs.map { it / 4 }
        val exposureTimes = evsTmp.map { ev ->
            T0 * Math.pow(2.0, ev.toDouble()).toFloat()
        }.toFloatArray()

        require(frames.size == exposureTimes.size) {
            "Number of frames must match number of exposure times"
        }
        val valid = frames.filter { !it.empty() && it.type() == CvType.CV_8UC3 && it.channels() == 3 }
        require(valid.size == frames.size) {
            "Invalid frame format"
        }

        // White balance
        val balancedFrames = valid.map { f ->
            val balanced = f.clone()
            val lab = Mat()
            Imgproc.cvtColor(balanced, lab, Imgproc.COLOR_RGB2Lab)
            val labChannels = ArrayList<Mat>(3)
            Core.split(lab, labChannels)

            // Среднее значение каналов A/B
            val meanA = Core.mean(labChannels[1]).`val`[0]
            val meanB = Core.mean(labChannels[2]).`val`[0]

            // Сдвигаем к нейтральным 128
            Core.subtract(labChannels[1], Scalar(meanA - 128), labChannels[1])
            Core.subtract(labChannels[2], Scalar(meanB - 128), labChannels[2])

            Core.merge(labChannels, lab)
            Imgproc.cvtColor(lab, balanced, Imgproc.COLOR_Lab2RGB)

            lab.release()
            labChannels.forEach { it.release() }

            balanced
        }

        val timesMat = Mat(frames.size, 1, CvType.CV_32F)
        timesMat.put(0, 0, exposureTimes)

        // HDR-алгоритм Дебевека и Малика
        // оценивает кривую отклика камеры Camera Response Function
        val calibrate = Photo.createCalibrateDebevec()
        val response = Mat()
        calibrate.process(balancedFrames, response, timesMat)

        val hdr = Mat()
        val mergeDebevec = Photo.createMergeDebevec()
        mergeDebevec.process(balancedFrames, hdr, timesMat, response)

        balancedFrames.forEach { it.release() }

        // Tonemap HDR
        val tonemap = Photo.createTonemap(2.2f)
        val ldrFloat = Mat()
        tonemap.process(hdr, ldrFloat)

        // Коррекция цвета
        val lab = Mat()
        Imgproc.cvtColor(ldrFloat, lab, Imgproc.COLOR_RGB2Lab)
        val labChannels = ArrayList<Mat>(3)
        Core.split(lab, labChannels)

        Core.multiply(labChannels[1], Scalar(0.92, 0.92, 0.92), labChannels[1])
        Core.multiply(labChannels[2], Scalar(0.96, 0.96, 0.96), labChannels[2])

        Core.merge(labChannels, lab)
        Imgproc.cvtColor(lab, ldrFloat, Imgproc.COLOR_Lab2RGB)

        lab.release()
        labChannels.forEach { it.release() }

        val result = Mat()
        ldrFloat.convertTo(result, CvType.CV_8UC3, 255.0)

        timesMat.release()
        response.release()
        hdr.release()
        ldrFloat.release()

        val styledResult = stylizeBeigeLook(result)
        result.release()

        return styledResult
    }


    fun processHdr(inputFrames: List<Mat>): Mat {
        val frames = inputFrames.filter { !it.empty() }

        if (frames.size < HDRFramesNumber) {
            Log.e("HDR", "Not enough frames")
            return frames.first().clone()
        }

        val w = frames[0].cols()
        val h = frames[0].rows()

        for (m in frames) {
            if (m.cols() != w || m.rows() != h) {
                Log.e("HDR", "Frame size mismatch")
                return frames[0].clone()
            }
        }

        val hdrFloat = Mat()

        try {
            val merge = Photo.createMergeMertens(
                1.0f,  // contrast weight
                1.0f,  // saturation weight
                1.0f   // exposure weight
            )
            merge.process(frames, hdrFloat)
        } catch (e: Exception) {
            Log.e("HDR", "MergeMertens failed", e)
            return frames[0].clone()
        }

        val ldr = Mat()
        hdrFloat.convertTo(
            ldr,
            CvType.CV_8UC3,
            255.0
        )
        hdrFloat.release()

        return ldr
    }
}

