package com.preciosai.photo_capture_plugin

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import android.graphics.*

/**
 * Конвертер ImageProxy → Bitmap.
 *
 * Автоматически определяет реальный формат буфера на первом кадре:
 *  - Если RGBA_8888 реально работает → copyPixelsFromBuffer (fastest)
 *  - Если устройство игнорирует RGBA_8888 (OnePlus и др.) → YUV → RenderScript Intrinsics
 *
 * Помечен @Suppress("DEPRECATION") так как RenderScript deprecated в API 31,
 * но продолжает работать и является наиболее совместимым решением.
 *
 * Все промежуточные буферы кешируются. Не потокобезопасен.
 *
 * build.gradle:
 *   android {
 *       defaultConfig {
 *           renderscriptTargetApi 21
 *           renderscriptSupportModeEnabled true
 *       }
 *   }
 *
 * Использование:
 *   private val converter = ImageProxyConverter(context)
 *   private var reusableBitmap: Bitmap? = null
 *
 *   override fun analyze(imageProxy: ImageProxy) {
 *       reusableBitmap = converter.convert(imageProxy, reusableBitmap)
 *       // используй reusableBitmap...
 *       imageProxy.close()
 *   }
 *
 *   override fun onDestroy() {
 *       converter.release()
 *       reusableBitmap?.recycle()
 *   }
 */
@Suppress("DEPRECATION")
class ImageProxyConverter(context: Context) {

    companion object {
        private const val TAG = "ImageProxyConverter"
    }

    // null = первый кадр, ещё не знаем
    private var rgbaSupported: Boolean? = null

    // RenderScript — для YUV пути
    private val rs: RenderScript = RenderScript.create(context)
    private val script: ScriptIntrinsicYuvToRGB =
        ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var inputAlloc:  Allocation? = null
    private var outputAlloc: Allocation? = null

    // Кешированные буферы для NV21
    private var cachedNv21: ByteArray? = null
    private var cachedRowBuffer: ByteArray? = null
    private var cachedRgbaBuffer: ByteBuffer? = null
    private var rawBitmap:       Bitmap? = null
    private var transformBitmap: Bitmap? = null
    private var transformCanvas: Canvas? = null
    private val rotateMatrix = Matrix()

    fun convert(
        image: ImageProxy,
        reusable: Bitmap?,
        rotationDegrees: Int = 0,
        isFrontCamera: Boolean = false,
    ): Bitmap {
        val useRgba = rgbaSupported ?: detectRgba(image).also { rgbaSupported = it }

        // Сырой кадр без трансформаций
        val raw = if (useRgba) convertRgba(image, rawBitmap) else convertYuv(image, rawBitmap)
        rawBitmap = raw

        val needsRotation = rotationDegrees != 0
        val needsFlip     = isFrontCamera

        if (!needsRotation && !needsFlip) return raw

        // Финальные размеры после поворота
        val outWidth  = if (rotationDegrees % 180 != 0) raw.height else raw.width
        val outHeight = if (rotationDegrees % 180 != 0) raw.width  else raw.height

        val out = getOrCreateBitmap(reusable, outWidth, outHeight)
        if (transformCanvas == null || transformBitmap !== out) {
            transformBitmap = out
            transformCanvas = Canvas(out)
        }

        rotateMatrix.reset()
        rotateMatrix.postTranslate(-raw.width / 2f, -raw.height / 2f)
        if (needsRotation) rotateMatrix.postRotate(rotationDegrees.toFloat())
        if (needsFlip)     rotateMatrix.postScale(-1f, 1f, 0f, 0f)
        rotateMatrix.postTranslate(outWidth / 2f, outHeight / 2f)

        transformCanvas!!.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
        transformCanvas!!.drawBitmap(raw, rotateMatrix, null)

        return out
    }

    fun release() {
        inputAlloc?.destroy()
        outputAlloc?.destroy()
        script.destroy()
        rs.destroy()
        inputAlloc      = null
        outputAlloc     = null
        cachedNv21      = null
        cachedRowBuffer = null
        cachedRgbaBuffer = null
        rawBitmap?.recycle()
        rawBitmap       = null
        transformBitmap = null
        transformCanvas = null
    }

    /**
     * Проверяем реальный размер буфера на первом кадре.
     * Некоторые устройства (OnePlus, Xiaomi) запрос RGBA_8888 игнорируют
     * и отдают YUV — тогда buffer.remaining() == width * height (только Y),
     * а не width * height * 4.
     */
    private fun detectRgba(image: ImageProxy): Boolean {
        val planesCount = image.planes.size
        val expected    = image.width * image.height * 4
        val actual      = image.planes[0].buffer.remaining()
        val rowStride   = image.planes[0].rowStride
        val pixelStride = image.planes[0].pixelStride
        val format      = image.format

        Log.d(TAG, "detectRgba: format=$format planes=$planesCount " +
                "size=${image.width}x${image.height} " +
                "buffer=$actual expected=$expected " +
                "rowStride=$rowStride pixelStride=$pixelStride")

        // planes.size == 1 → однозначно RGBA (YUV всегда 3 плоскости)
        // actual == expected → стандартная проверка по размеру
        val supported = image.planes.size == 1 || actual == expected

        Log.d(TAG, "RGBA_8888 supported: $supported " +
                "(planes=${image.planes.size}, buffer=$actual, expected=$expected)")
        return supported
    }

    private fun convertRgba(image: ImageProxy, reusable: Bitmap?): Bitmap {
        val width       = image.width
        val height      = image.height
        val plane       = image.planes[0]
        val rowStride   = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer      = plane.buffer.also { it.rewind() }

        val bitmap = getOrCreateBitmap(reusable, width, height)

        if (rowStride == width * pixelStride) {
            // Нет паддинга — быстрый путь
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            // Есть паддинг (OnePlus и др.) — копируем построчно
            val rowBytes = width * pixelStride
            val tightSize = width * height * pixelStride
            val tight = cachedRgbaBuffer?.takeIf { it.capacity() == tightSize }
                ?: ByteBuffer.allocateDirect(tightSize).also { cachedRgbaBuffer = it }
            tight.rewind()

            val rowBuf = cachedRowBuffer?.takeIf { it.size >= rowStride }
                ?: ByteArray(rowStride).also { cachedRowBuffer = it }

            for (row in 0 until height) {
                buffer.position(row * rowStride)
                val toRead = minOf(rowStride, buffer.remaining())
                buffer.get(rowBuf, 0, toRead)
                tight.put(rowBuf, 0, rowBytes)
            }

            tight.rewind()
            bitmap.copyPixelsFromBuffer(tight)
        }

        return bitmap
    }

    private fun convertYuv(image: ImageProxy, reusable: Bitmap?): Bitmap {
        val width  = image.width
        val height = image.height
        val bitmap = getOrCreateBitmap(reusable, width, height)

        val nv21     = getOrCreateNv21(width, height)
        fillNv21(image, nv21, width, height)

        val inAlloc  = getOrCreateInputAlloc(nv21.size)
        val outAlloc = getOrCreateOutputAlloc(bitmap, width, height)

        inAlloc.copyFrom(nv21)
        script.forEach(outAlloc)
        outAlloc.copyTo(bitmap)

        return bitmap
    }

    private fun getOrCreateInputAlloc(size: Int): Allocation {
        val current = inputAlloc
        return if (current != null && current.bytesSize == size) {
            current
        } else {
            current?.destroy()
            val type = Type.Builder(rs, Element.U8(rs)).setX(size).create()
            Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT).also {
                inputAlloc = it
                script.setInput(it)
            }
        }
    }

    private fun getOrCreateOutputAlloc(bitmap: Bitmap, width: Int, height: Int): Allocation {
        val current = outputAlloc
        return if (current != null && current.type.x == width && current.type.y == height) {
            current
        } else {
            current?.destroy()
            Allocation.createFromBitmap(rs, bitmap).also { outputAlloc = it }
        }
    }

    private fun getOrCreateBitmap(reusable: Bitmap?, width: Int, height: Int): Bitmap {
        return if (reusable != null && reusable.width == width && reusable.height == height) {
            reusable
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    private fun getOrCreateNv21(width: Int, height: Int): ByteArray {
        val size    = width * height * 3 / 2
        val current = cachedNv21
        return if (current != null && current.size == size) current
        else ByteArray(size).also { cachedNv21 = it }
    }

    /**
     * Заполняет out[] в формате NV21: Y-плоскость, затем interleaved V,U.
     *
     * planes[0] = Y, planes[1] = U (Cb), planes[2] = V (Cr)
     * NV21 layout: YYYY... VU VU VU...
     */
    private fun fillNv21(image: ImageProxy, out: ByteArray, width: Int, height: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val ySize  = width * height

        // Y (pixelStride всегда 1)
        yPlane.buffer.rewind()
        copyPlane(
            buffer         = yPlane.buffer,
            rowStride      = yPlane.rowStride,
            pixelStride    = 1,
            width          = width,
            height         = height,
            out            = out,
            outOffset      = 0,
            outPixelStride = 1,
        )

        if (vPlane.pixelStride == 2) {
            // Semi-planar (большинство устройств):
            // V-буфер содержит V0 U0 V1 U1 ... — готовый NV21 UV layout.
            // Один bulk-copy вместо побайтового цикла.
            val vBuf = vPlane.buffer.also { it.rewind() }
            val uvByteCount = minOf(vBuf.remaining(), ySize / 2)
            vBuf.get(out, ySize, uvByteCount)
        } else {
            // Planar (редкий случай): V и U раздельно → перемежаем вручную.
            vPlane.buffer.rewind()
            uPlane.buffer.rewind()
            copyPlane(
                buffer         = vPlane.buffer,
                rowStride      = vPlane.rowStride,
                pixelStride    = 1,
                width          = width / 2,
                height         = height / 2,
                out            = out,
                outOffset      = ySize,
                outPixelStride = 2,
            )
            copyPlane(
                buffer         = uPlane.buffer,
                rowStride      = uPlane.rowStride,
                pixelStride    = 1,
                width          = width / 2,
                height         = height / 2,
                out            = out,
                outOffset      = ySize + 1,
                outPixelStride = 2,
            )
        }
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int,
        outPixelStride: Int,
    ) {
        val bytesPerRow = (width - 1) * pixelStride + 1
        val rowBuffer   = getOrCreateRowBuffer(bytesPerRow)
        var outIndex    = outOffset

        for (row in 0 until height) {
            buffer.position(row * rowStride)

            val toRead = minOf(bytesPerRow, buffer.remaining())
            if (toRead <= 0) {
                Log.w(TAG, "copyPlane: buffer exhausted at row $row")
                break
            }
            buffer.get(rowBuffer, 0, toRead)

            if (pixelStride == 1 && outPixelStride == 1) {
                // Быстрый путь: обе стороны плотные → блочное копирование
                System.arraycopy(rowBuffer, 0, out, outIndex, width)
                outIndex += width
            } else {
                for (col in 0 until width) {
                    val srcIdx = col * pixelStride
                    if (srcIdx < toRead) out[outIndex] = rowBuffer[srcIdx]
                    outIndex += outPixelStride
                }
            }
        }
    }

    private fun getOrCreateRowBuffer(minSize: Int): ByteArray {
        val current = cachedRowBuffer
        return if (current != null && current.size >= minSize) current
        else ByteArray(minSize).also { cachedRowBuffer = it }
    }
}