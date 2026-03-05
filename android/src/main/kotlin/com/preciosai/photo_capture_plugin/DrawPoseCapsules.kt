package com.preciosai.photo_capture_plugin

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.atan2
import kotlin.math.hypot


private data class Bone(val start: Int, val end: Int, val partKey: String)

class CapsulePoseRenderer {

    // Кисть для четкой границы
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Кисть для полупрозрачной заливки внутри капсулы
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val colorGood = Color.parseColor("#39FF14")
    private val colorWarning = Color.parseColor("#FF9100")
    private val colorBad = Color.parseColor("#FF003C")
    private val limbs = listOf(
        Bone(5, 7, "left_hand"), Bone(7, 9, "left_hand"),
        Bone(6, 8, "right_hand"), Bone(8, 10, "right_hand"),
        Bone(11, 13, "left_leg"), Bone(13, 15, "left_leg"),
        Bone(12, 14, "right_leg"), Bone(14, 16, "right_leg")
    )

    private fun getColorForScore(score: Float): Int {
        return when {
            score >= 0.8f -> colorGood
            score >= 0.5f -> colorWarning
            else -> colorBad
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
        fillPaint.color = color
        fillPaint.alpha = 70

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)

        canvas.drawRoundRect(rect, ry, ry, fillPaint)
        canvas.drawRoundRect(rect, ry, ry, strokePaint)

        canvas.restore()
    }

    fun drawPose(
        canvas: Canvas,
        keypoints: Array<PointF?>,
        details: Map<String, Float?>?
    ) {
        if (keypoints.size < 17) return

        for (bone in limbs) {
            val p1 = keypoints[bone.start]
            val p2 = keypoints[bone.end]

            if (p1 != null && p2 != null) {
                var color = Color.parseColor("#00E5FF")
                if (details != null) {
                    val score = details[bone.partKey] ?: 0f
                    Log.d("CapsulePoseRenderer", "Bone: ${bone.partKey}, Score: $score")
                    color = getColorForScore(score)
                }

                drawCapsule(
                    canvas = canvas,
                    p1 = p1,
                    p2 = p2,
                    color = color,
                    capsuleThickness = 50f,
                    gap = 10f
                )
            }
        }

        val leftShoulder = keypoints[5]
        val rightShoulder = keypoints[6]
        val leftHip = keypoints[11]
        val rightHip = keypoints[12]
        val nose = keypoints[0]

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
            if (details != null) {
                val shouldersScore = details["shoulders"] ?: 0f
                val pelvisScore = details["pelvis"] ?: 0f
                val torsoScore = (shouldersScore + pelvisScore) / 2f
                torsoColor = getColorForScore(torsoScore)
            }

            // Рисуем позвоночник
            drawCapsule(
                canvas = canvas,
                p1 = neck,
                p2 = pelvis,
                color = torsoColor,
                capsuleThickness = 80f,
                gap = 15f
            )

            // Для головы можно использовать показатель глаз или ушей
            // TODO поменять голову
            if (nose != null) {
                var headColor = Color.parseColor("#00E5FF")

                if (details != null) {
                    headColor = getColorForScore(details["eyes"] ?: 0f)
                }

                drawCapsule(
                    canvas = canvas,
                    p1 = nose,
                    p2 = neck,
                    color = headColor,
                    capsuleThickness = 50f,
                    gap = 10f
                )
            }
        }
    }
}
