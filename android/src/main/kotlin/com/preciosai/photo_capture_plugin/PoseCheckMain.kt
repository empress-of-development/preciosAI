
package com.preciosai.photo_capture_plugin

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import android.util.Log
import kotlin.math.exp
import android.graphics.RectF
import org.opencv.core.Size


data class PoseComparisonResult(
    val overallScore: Float,
    val details: Map<String, Float?>
)

// PoseEvaluator не актуальный
class PoseEvaluator(
    private val alpha: Float = 0.2f,
    private val maxToleranceDegrees: Float = 45f
) {
    // Память для сглаживания кадров
    private var smoothedOverall: Float? = null
    private val smoothedDetails = mutableMapOf<String, Float>()

    private fun getAngleBetweenVectors(v1x: Float, v1y: Float, v2x: Float, v2y: Float): Float {
        val norm1 = hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
        val norm2 = hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
        if (norm1 == 0f || norm2 == 0f) return 180f

        val dotProduct = (v1x * v2x) + (v1y * v2y)
        val cosTheta = (dotProduct / (norm1 * norm2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    private fun getBoneDirectionDiff(
        refP1: Pair<Float, Float>, refP2: Pair<Float, Float>,
        usrP1: Pair<Float, Float>, usrP2: Pair<Float, Float>
    ): Float {
        val vRefX = refP2.first - refP1.first
        val vRefY = refP2.second - refP1.second
        val vUsrX = usrP2.first - usrP1.first
        val vUsrY = usrP2.second - usrP1.second
        return getAngleBetweenVectors(vRefX, vRefY, vUsrX, vUsrY)
    }

    private fun getJointAngle(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>): Float {
        val v1x = p1.first - p2.first
        val v1y = p1.second - p2.second
        val v2x = p3.first - p2.first
        val v2y = p3.second - p2.second
        return getAngleBetweenVectors(v1x, v1y, v2x, v2y)
    }

    fun compareAndSmooth(
        poseRef: PredictionObj,
        poseUsr: PredictionObj,
        confThreshold: Float = 0.4f
    ): PoseComparisonResult {

        val kpRef = poseRef.keypoints
        val kpUsr = poseUsr.keypoints

        if (kpRef.xy.size < 17 || kpUsr.xy.size < 17) {
            return PoseComparisonResult(smoothedOverall ?: 0f, emptyMap())
        }

        val rawDetails = mutableMapOf<String, Float?>()
        val allScores = mutableListOf<Float>()

        val complexLimbs = mapOf(
            "left_hand" to Triple(5, 7, 9),
            "right_hand" to Triple(6, 8, 10),
            "left_leg" to Triple(11, 13, 15),
            "right_leg" to Triple(12, 14, 16)
        )

        for ((limbName, indices) in complexLimbs) {
            val (idx1, idx2, idx3) = indices

            // Проверяем видимость точек
            if (kpRef.scores.getOrElse(idx1) { 0f } < confThreshold ||
                kpRef.scores.getOrElse(idx2) { 0f } < confThreshold ||
                kpRef.scores.getOrElse(idx3) { 0f } < confThreshold ||
                kpUsr.scores.getOrElse(idx1) { 0f } < confThreshold ||
                kpUsr.scores.getOrElse(idx2) { 0f } < confThreshold ||
                kpUsr.scores.getOrElse(idx3) { 0f } < confThreshold) {
                continue
            }

            // Ошибка направления костей
            val bone1Diff = getBoneDirectionDiff(kpRef.xy[idx1], kpRef.xy[idx2], kpUsr.xy[idx1], kpUsr.xy[idx2])
            val bone2Diff = getBoneDirectionDiff(kpRef.xy[idx2], kpRef.xy[idx3], kpUsr.xy[idx2], kpUsr.xy[idx3])

            // Ошибка сгиба сустава
            val jointRefAngle = getJointAngle(kpRef.xy[idx1], kpRef.xy[idx2], kpRef.xy[idx3])
            val jointUsrAngle = getJointAngle(kpUsr.xy[idx1], kpUsr.xy[idx2], kpUsr.xy[idx3])
            val jointDiff = abs(jointRefAngle - jointUsrAngle)

            // Находим самую грубую ошибку
            val worstErrorDegrees = maxOf(bone1Diff, bone2Diff, jointDiff)

            // Переводим ошибку в процент совпадения (0.0 - 1.0)
            val score = (1f - (worstErrorDegrees / maxToleranceDegrees)).coerceIn(0f, 1f)

            rawDetails[limbName] = score
            allScores.add(score)
        }

        val simpleBones = mapOf(
            "eyes" to Pair(1, 2),
            "ears" to Pair(3, 4),
            "shoulders" to Pair(5, 6),
            "pelvis" to Pair(11, 12),
            "left_side" to Pair(5, 11),
            "right_side" to Pair(6, 12)
        )

        for ((boneName, indices) in simpleBones) {
            val (idx1, idx2) = indices

            if (kpRef.scores.getOrElse(idx1) { 0f } < confThreshold ||
                kpRef.scores.getOrElse(idx2) { 0f } < confThreshold ||
                kpUsr.scores.getOrElse(idx1) { 0f } < confThreshold ||
                kpUsr.scores.getOrElse(idx2) { 0f } < confThreshold) {
                continue
            }

            val boneDiff = getBoneDirectionDiff(kpRef.xy[idx1], kpRef.xy[idx2], kpUsr.xy[idx1], kpUsr.xy[idx2])
            val score = (1f - (boneDiff / maxToleranceDegrees)).coerceIn(0f, 1f)

            rawDetails[boneName] = score
            allScores.add(score)
        }

        val rawOverall = if (allScores.isNotEmpty()) allScores.average().toFloat() else 0f

        val finalOverall = if (smoothedOverall == null) {
            rawOverall
        } else {
            (alpha * rawOverall) + ((1f - alpha) * smoothedOverall!!)
        }
        smoothedOverall = finalOverall

        val finalDetails = mutableMapOf<String, Float?>()
        for ((part, rawScore) in rawDetails) {
            if (rawScore != null) {
                val prevScore = smoothedDetails[part]
                val newScore = if (prevScore == null) {
                    rawScore
                } else {
                    (alpha * rawScore) + ((1f - alpha) * prevScore)
                }
                smoothedDetails[part] = newScore
                finalDetails[part] = newScore
            } else {
                smoothedDetails.remove(part)
                finalDetails[part] = null
            }
        }

        return PoseComparisonResult(finalOverall, finalDetails)
    }
}

fun cosineSimilarity(
    poseRef: PredictionObj,
    poseUsr: PredictionObj,
    pose2ImageShape: Size,
    confThreshold: Float = 0.4f,
    maxToleranceDegrees: Float = 45f
): PoseComparisonResult {

    val kpRef = poseRef.keypoints
    val kpUsr = poseUsr.keypoints
    val details = mutableMapOf<String, Float?>()
    val allScores = mutableListOf<Float>()

    if (kpRef.xy.size < 17 || kpUsr.xy.size < 17) {
        return PoseComparisonResult(0f, details)
    }

    fun getAngleBetweenVectors(v1x: Float, v1y: Float, v2x: Float, v2y: Float): Float {
        val norm1 = hypot(v1x.toDouble(), v1y.toDouble()).toFloat()
        val norm2 = hypot(v2x.toDouble(), v2y.toDouble()).toFloat()
        if (norm1 == 0f || norm2 == 0f) return 180f
        val dotProduct = (v1x * v2x) + (v1y * v2y)
        val cosTheta = (dotProduct / (norm1 * norm2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }

    fun getBoneDirectionDiff(p1Ref: Pair<Float, Float>, p2Ref: Pair<Float, Float>,
                             p1Usr: Pair<Float, Float>, p2Usr: Pair<Float, Float>): Float {
        return getAngleBetweenVectors(
            p2Ref.first - p1Ref.first, p2Ref.second - p1Ref.second,
            p2Usr.first - p1Usr.first, p2Usr.second - p1Usr.second
        )
    }

    fun getJointAngle(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>): Float {
        return getAngleBetweenVectors(
            p1.first - p2.first, p1.second - p2.second,
            p3.first - p2.first, p3.second - p2.second
        )
    }

    // Проверка на правильное расположение модели
    // идем по каскаду по точкам снизу вверх и ищем те, которые есть и на референсе, и на кадре
    // по этой точке проверяем дистанцию между ними

    val fallbackAnchors = listOf(
        Pair(11, 12), // Приоритет 1: Таз (Центр масс)
        Pair(5, 6),   // Приоритет 2: Плечи (Портрет по пояс)
        Pair(3, 4),   // Приоритет 3: Уши (Лицо крупным планом)
        Pair(1, 2)    // Приоритет 4: Глаза (Запасной вариант для лица)
    )

    val maxGlobalShiftTolerance: Float = 0.15f
    var anchorFound = false
    val aspectRatio = pose2ImageShape.width.toFloat() / pose2ImageShape.height.toFloat()

    for ((idx1, idx2) in fallbackAnchors) {
        if (kpRef.scores.getOrElse(idx1) { 0f } >= confThreshold &&
            kpRef.scores.getOrElse(idx2) { 0f } >= confThreshold &&
            kpUsr.scores.getOrElse(idx1) { 0f } >= confThreshold &&
            kpUsr.scores.getOrElse(idx2) { 0f } >= confThreshold) {

            val refAnchorX = (kpRef.xyn[idx1].first + kpRef.xyn[idx2].first) / 2f
            val refAnchorY = (kpRef.xyn[idx1].second + kpRef.xyn[idx2].second) / 2f

            val usrAnchorX = (kpUsr.xyn[idx1].first + kpUsr.xyn[idx2].first) / 2f
            val usrAnchorY = (kpUsr.xyn[idx1].second + kpUsr.xyn[idx2].second) / 2f

            val diffX = refAnchorX - usrAnchorX
            val diffY = (refAnchorY - usrAnchorY) / aspectRatio

            val displacement = hypot(diffX.toDouble(), diffY.toDouble()).toFloat()
            val positionScore = (1f - (displacement / maxGlobalShiftTolerance)).coerceIn(0f, 1f)

            details["location"] = positionScore
            allScores.add(positionScore)

            anchorFound = true
            break
        }
    }

    if (!anchorFound) {
        details["location"] = null
    }

    val complexLimbs = mapOf(
        "left_hand" to Triple(5, 7, 9),
        "right_hand" to Triple(6, 8, 10),
        "left_leg" to Triple(11, 13, 15),
        "right_leg" to Triple(12, 14, 16)
    )

    for ((limbName, indices) in complexLimbs) {
        val (idx1, idx2, idx3) = indices

        if (kpRef.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpRef.scores.getOrElse(idx2) { 0f } < confThreshold ||
            kpRef.scores.getOrElse(idx3) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx2) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx3) { 0f } < confThreshold) {

            details[limbName] = null
            continue
        }

        val bone1Diff = getBoneDirectionDiff(kpRef.xy[idx1], kpRef.xy[idx2], kpUsr.xy[idx1], kpUsr.xy[idx2])
        val bone2Diff = getBoneDirectionDiff(kpRef.xy[idx2], kpRef.xy[idx3], kpUsr.xy[idx2], kpUsr.xy[idx3])
        val jointRefAngle = getJointAngle(kpRef.xy[idx1], kpRef.xy[idx2], kpRef.xy[idx3])
        val jointUsrAngle = getJointAngle(kpUsr.xy[idx1], kpUsr.xy[idx2], kpUsr.xy[idx3])
        val jointDiff = abs(jointRefAngle - jointUsrAngle)

        val worstErrorDegrees = maxOf(bone1Diff, bone2Diff, jointDiff)
        val score = (1f - (worstErrorDegrees / maxToleranceDegrees)).coerceIn(0f, 1f)
        Log.d("PoseCheckMain", "$limbName: bone1Diff $bone1Diff, bone2Diff $bone2Diff, jointDiff $jointDiff, worstErrorDegrees $worstErrorDegrees, score $score")

        details[limbName] = score
        allScores.add(score)
    }

    val simpleBones = mapOf(
        "eyes" to Pair(1, 2),
        "ears" to Pair(3, 4),
        "shoulders" to Pair(5, 6),
        "pelvis" to Pair(11, 12),
        "left_side" to Pair(5, 11),
        "right_side" to Pair(6, 12)
    )

    for ((boneName, indices) in simpleBones) {
        val (idx1, idx2) = indices

        if (kpRef.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpRef.scores.getOrElse(idx2) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx2) { 0f } < confThreshold) {

            details[boneName] = null
            continue
        }

        val boneDiff = getBoneDirectionDiff(kpRef.xy[idx1], kpRef.xy[idx2], kpUsr.xy[idx1], kpUsr.xy[idx2])
        val score = (1f - (boneDiff / maxToleranceDegrees)).coerceIn(0f, 1f)

        details[boneName] = score
        allScores.add(score)
    }

    val overallScore = if (allScores.isNotEmpty()) {
        val averageScore = allScores.average().toFloat()
        val minScore = allScores.minOrNull() ?: 0f

        // штраф за самую несовпадающую часть
        val penaltyWeight = 0.35f

        (averageScore * (1f - penaltyWeight)) + (minScore * penaltyWeight)
    } else {
        0f
    }

    Log.d("PoseCheckMain", "details: $details")
    return PoseComparisonResult(overallScore, details)
}
