package com.preciosai.photo_capture_plugin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.atan2
import kotlin.math.hypot
import androidx.core.graphics.ColorUtils


private data class Bone(val start: Int, val end: Int, val partKey: String)

class CapsulePoseRenderer @JvmOverloads constructor(private val poseMode: String = "COCO") {

    // Кисть для четкой границы
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Кисть для полупрозрачной заливки внутри капсулы
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    companion object {
        private val colorGood = Color.parseColor("#39FF14")
        private val colorWarning = Color.parseColor("#FF9100")
        private val colorBad = Color.parseColor("#FF003C")
        private val limbsConsts = mapOf(
            "COCO" to listOf(
                Bone(5, 7, "right_hand"), Bone(7, 9, "right_hand"),
                Bone(6, 8, "left_hand"), Bone(8, 10, "left_hand"),
                Bone(11, 13, "right_leg"), Bone(13, 15, "right_leg"),
                Bone(12, 14, "left_leg"), Bone(14, 16, "left_leg")
            ),
            "MediaPipe" to listOf(
                Bone(12, 14, "left_hand"), Bone(14, 16, "left_hand"),
                Bone(11, 13, "right_hand"), Bone(13, 15, "right_hand"),
                Bone(24, 26, "left_leg"), Bone(26, 28, "left_leg"),
                Bone(23, 25, "right_leg"), Bone(25, 27, "right_leg"),
                Bone(12, 11, "shoulders")
            )
        )
    }
    private val limbs: List<Bone> = limbsConsts[poseMode]!!

    private fun getColorForScore(score: Float): Int {
        val safeScore = score.coerceIn(0f, 1f)

        return when {
            safeScore <= 0.5f -> {
                val ratio = safeScore / 0.5f
                ColorUtils.blendARGB(colorBad, colorWarning, ratio)
            }
            safeScore <= 0.8f -> {
                val ratio = (safeScore - 0.5f) / 0.3f
                ColorUtils.blendARGB(colorWarning, colorGood, ratio)
            }
            else -> {
                colorGood
            }
        }
    }

    fun drawCapsule(
        canvas: Canvas,
        p1: PointF,
        p2: PointF,
        color: Int,
        capsuleThickness: Float = 60f,
        gap: Float = 15f
    ) {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // если точки слишком близко друг к другу, не рисуем капсулу
        if (distance <= gap * 2) return

        // Вычисляем угол наклона в градусах
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        val cx = (p1.x + p2.x) / 2
        val cy = (p1.y + p2.y) / 2

        // Вычисляем длину капсулы
        val length = distance - (gap * 2)

        // Скругление
        val rx = length / 2
        val ry = capsuleThickness / 2
        val rect = RectF(-rx, -ry, rx, ry)

        strokePaint.color = color
        strokePaint.alpha = 80

        fillPaint.color = color
        fillPaint.alpha = 40

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)

        canvas.drawRoundRect(rect, ry, ry, fillPaint)
        // canvas.drawRoundRect(rect, ry, ry, strokePaint)

        canvas.restore()
    }

    fun drawPose(
        canvas: Canvas,
        keypoints: Array<PointF?>,
        details: Map<String, Float?>
    ) {
        if (keypoints.size < 17) return

        for (bone in limbs) {
            val p1 = keypoints[bone.start]
            val p2 = keypoints[bone.end]
            val score = details[bone.partKey]

            if (p1 != null && p2 != null && score != null) {
                // var color = Color.parseColor("#00E5FF")
                // color = getColorForScore(score)

                drawCapsule(
                    canvas = canvas,
                    p1 = p1,
                    p2 = p2,
                    color = getColorForScore(score),
                    capsuleThickness = 50f,
                    gap = 10f
                )
            }
        }

        val leftShoulder = if (poseMode == "COCO") keypoints[6] else keypoints[12]
        val rightShoulder = if (poseMode == "COCO") keypoints[5] else keypoints[11]
        val leftHip = if (poseMode == "COCO") keypoints[12] else keypoints[24]
        val rightHip = if (poseMode == "COCO") keypoints[11] else keypoints[23]
        val nose = keypoints[0]
        val leftEar = if (poseMode == "COCO") keypoints[4] else keypoints[8]
        val rightEar = if (poseMode == "COCO") keypoints[3] else keypoints[7]

        val leftEye = if (poseMode == "COCO") keypoints[2] else keypoints[5]
        val rightEye = if (poseMode == "COCO") keypoints[1] else keypoints[2]

        if (leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null
        ) {
            val neck = PointF(
                (leftShoulder.x + rightShoulder.x) / 2,
                (leftShoulder.y + rightShoulder.y) / 2
            )
            val pelvis = PointF(
                (leftHip.x + rightHip.x) / 2,
                (leftHip.y + rightHip.y) / 2
            )

            // Для торса берем среднее значение между плечами и тазом
            var torsoColor = Color.parseColor("#00E5FF")
            val shouldersScore = details["shoulders"] ?: 0f
            val pelvisScore = details["pelvis"] ?: 0f
            val torsoScore = (shouldersScore + pelvisScore) / 2f
            torsoColor = getColorForScore(torsoScore)

            // Рисуем позвоночник
            drawCapsule(
                canvas = canvas,
                p1 = neck,
                p2 = pelvis,
                color = torsoColor,
                capsuleThickness = 80f,
                gap = 15f
            )
        }

        if ((leftEar != null || leftEye != null) && (rightEar != null || rightEye != null)) {
            var headColor = Color.parseColor("#00E5FF")

            if (details != null) {
                headColor = getColorForScore(details["eyes"] ?: 0f)
            }

            drawCapsule(
                canvas = canvas,
                p1 = if (leftEar != null) leftEar else leftEye!!,
                p2 = if (rightEar != null) rightEar else rightEye!!,
                color = headColor,
                capsuleThickness = 50f,
                gap = 10f
            )
        }

    }
}
