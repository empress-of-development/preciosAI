
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


class PoseComparator(
    private val mode: String = "MediaPipe",
    private val confThreshold: Float = 0.3f,
    private val maxToleranceDegrees: Float = 45f,
    private val alpha: Float = 0.4f,
    private val maxMissingFrames: Int = 10 // На сколько кадров замораживаем линию, если она пропала
) {

    public var previousResult: PoseComparisonResult? = null
    // Память для счетчика пропущенных кадров по каждой части тела
    private val missingFramesCount = mutableMapOf<String, Int>()

    private val minKeypoints: Int
    private val fallbackAnchors: List<Pair<Int, Int>>
    private val complexLimbs: Map<String, Triple<Int, Int, Int>>
    private val simpleBones: Map<String, Pair<Int, Int>>

    init {
        if (mode == "COCO") {
            minKeypoints = 17
            fallbackAnchors = listOf(Pair(11, 12), Pair(5, 6), Pair(3, 4), Pair(1, 2))
            complexLimbs = mapOf(
                "right_hand" to Triple(5, 7, 9), "left_hand" to Triple(6, 8, 10),
                "right_leg" to Triple(11, 13, 15), "left_leg" to Triple(12, 14, 16)
            )
            simpleBones = mapOf(
                "eyes" to Pair(1, 2), "ears" to Pair(3, 4), "shoulders" to Pair(5, 6),
                "pelvis" to Pair(11, 12), "right_side" to Pair(5, 11), "left_side" to Pair(6, 12)
            )
        } else {
            minKeypoints = 33
            fallbackAnchors = listOf(Pair(23, 24), Pair(11, 12), Pair(7, 8), Pair(2, 5))
            complexLimbs = mapOf(
                "right_hand" to Triple(11, 13, 15), "left_hand" to Triple(12, 14, 16),
                "right_leg" to Triple(23, 25, 27), "left_leg" to Triple(24, 26, 28)
            )
            simpleBones = mapOf(
                "eyes" to Pair(2, 5), "ears" to Pair(7, 8), "shoulders" to Pair(11, 12),
                "pelvis" to Pair(23, 24), "right_side" to Pair(11, 23), "left_side" to Pair(12, 24)
            )
        }
    }

    fun reset() {
        previousResult = null
        missingFramesCount.clear()
    }

    fun compare(
        poseRef: PredictionObj,
        poseUsr: PredictionObj,
        pose2ImageShape: Size
    ): PoseComparisonResult {

        val kpRef = poseRef.keypoints
        val kpUsr = poseUsr.keypoints

        if (kpRef.xy.size < minKeypoints || kpUsr.xy.size < minKeypoints) {
            val prev = previousResult
            if (prev != null && prev.overallScore > 0.05f) {
                val decay = 0.9f
                val decayedScore = prev.overallScore * decay
                val decayedDetails = mutableMapOf<String, Float?>()
                for ((key, value) in prev.details) {
                    decayedDetails[key] = if (value != null) {
                        val newValue = value * decay
                        if (newValue > 0.05f) newValue else null
                    } else null
                }
                val fadingResult = PoseComparisonResult(decayedScore, decayedDetails)
                previousResult = fadingResult
                return fadingResult
            } else {
                reset()
                return PoseComparisonResult(0f, emptyMap())
            }
        }

        val rawDetails = mutableMapOf<String, Float?>()
        val refHasLimb = mutableMapOf<String, Boolean>()

        fun getUsrThresh(key: String): Float {
            val wasVisible = previousResult?.details?.get(key) != null
            return if (wasVisible) confThreshold * 0.5f else confThreshold
        }

        val use3D = false
        fun getZ(kp: Keypoints, idx: Int): Float {
            if (!use3D) return 0f
            return kp.zn!![idx] * pose2ImageShape.width.toFloat()
        }

        fun getBoneDirectionDiff(idx1: Int, idx2: Int): Float {
            return getAngleBetweenVectors(
                kpRef.xy[idx2].first - kpRef.xy[idx1].first, kpRef.xy[idx2].second - kpRef.xy[idx1].second, 0f,
                kpUsr.xy[idx2].first - kpUsr.xy[idx1].first, kpUsr.xy[idx2].second - kpUsr.xy[idx1].second, 0f
            )
        }

        fun getJointAngle(kp: Keypoints, idx1: Int, idx2: Int, idx3: Int): Float {
            return getAngleBetweenVectors(
                kp.xy[idx1].first - kp.xy[idx2].first, kp.xy[idx1].second - kp.xy[idx2].second, getZ(kp, idx1) - getZ(kp, idx2),
                kp.xy[idx3].first - kp.xy[idx2].first, kp.xy[idx3].second - kp.xy[idx2].second, getZ(kp, idx3) - getZ(kp, idx2)
            )
        }

        val maxGlobalShiftTolerance = 0.15f
        val aspectRatio = pose2ImageShape.width.toFloat() / pose2ImageShape.height.toFloat()

        var anchorFoundInRef = false
        var anchorScore: Float? = null

        for ((idx1, idx2) in fallbackAnchors) {
            val refValid = kpRef.scores.getOrElse(idx1) { 0f } >= confThreshold &&
                    kpRef.scores.getOrElse(idx2) { 0f } >= confThreshold
            if (refValid) {
                anchorFoundInRef = true
                val usrThresh = getUsrThresh("location")
                val usrValid = kpUsr.scores.getOrElse(idx1) { 0f } >= usrThresh &&
                        kpUsr.scores.getOrElse(idx2) { 0f } >= usrThresh
                if (usrValid) {
                    val refX = (kpRef.xyn[idx1].first + kpRef.xyn[idx2].first) / 2f
                    val refY = (kpRef.xyn[idx1].second + kpRef.xyn[idx2].second) / 2f
                    val usrX = (kpUsr.xyn[idx1].first + kpUsr.xyn[idx2].first) / 2f
                    val usrY = (kpUsr.xyn[idx1].second + kpUsr.xyn[idx2].second) / 2f

                    val displacement = hypot((refX - usrX).toDouble(), ((refY - usrY) / aspectRatio).toDouble()).toFloat()
                    anchorScore = (1f - (displacement / maxGlobalShiftTolerance)).coerceIn(0f, 1f)
                    break // Нашли совпадение
                }
            }
        }
        refHasLimb["location"] = anchorFoundInRef
        rawDetails["location"] = anchorScore

        for ((limbName, indices) in complexLimbs) {
            val (idx1, idx2, idx3) = indices
            val refValid = kpRef.scores.getOrElse(idx1) { 0f } >= confThreshold &&
                    kpRef.scores.getOrElse(idx2) { 0f } >= confThreshold &&
                    kpRef.scores.getOrElse(idx3) { 0f } >= confThreshold

            refHasLimb[limbName] = refValid

            if (!refValid) {
                rawDetails[limbName] = null
                continue
            }

            val usrThresh = getUsrThresh(limbName)
            val usrValid = kpUsr.scores.getOrElse(idx1) { 0f } >= usrThresh &&
                    kpUsr.scores.getOrElse(idx2) { 0f } >= usrThresh &&
                    kpUsr.scores.getOrElse(idx3) { 0f } >= usrThresh

            if (!usrValid) {
                rawDetails[limbName] = null
                continue
            }

            val worstError = maxOf(
                getBoneDirectionDiff(idx1, idx2),
                getBoneDirectionDiff(idx2, idx3),
                kotlin.math.abs(getJointAngle(kpRef, idx1, idx2, idx3) - getJointAngle(kpUsr, idx1, idx2, idx3))
            )
            rawDetails[limbName] = (1f - (worstError / maxToleranceDegrees)).coerceIn(0f, 1f)
        }

        for ((boneName, indices) in simpleBones) {
            val (idx1, idx2) = indices
            val refValid = kpRef.scores.getOrElse(idx1) { 0f } >= confThreshold &&
                    kpRef.scores.getOrElse(idx2) { 0f } >= confThreshold

            refHasLimb[boneName] = refValid

            if (!refValid) {
                rawDetails[boneName] = null
                continue
            }

            val usrThresh = getUsrThresh(boneName)
            val usrValid = kpUsr.scores.getOrElse(idx1) { 0f } >= usrThresh &&
                    kpUsr.scores.getOrElse(idx2) { 0f } >= usrThresh

            if (!usrValid) {
                rawDetails[boneName] = null
                continue
            }

            val boneDiff = getBoneDirectionDiff(idx1, idx2)
            rawDetails[boneName] = (1f - (boneDiff / maxToleranceDegrees)).coerceIn(0f, 1f)
        }

        val prev = previousResult
        val finalDetails = mutableMapOf<String, Float?>()
        val allKeys = listOf("location") + complexLimbs.keys + simpleBones.keys

        for (key in allKeys) {
            val isRequiredByRef = refHasLimb[key] ?: false
            val currentValue = rawDetails[key]
            val prevValue = prev?.details?.get(key)

            if (!isRequiredByRef) {
                finalDetails[key] = null
                missingFramesCount[key] = 0
            } else {
                if (currentValue != null) {
                    missingFramesCount[key] = 0
                    finalDetails[key] = if (prevValue != null) {
                        (alpha * currentValue) + ((1f - alpha) * prevValue)
                    } else currentValue
                } else {
                    val missed = missingFramesCount.getOrElse(key) { 0 } + 1
                    missingFramesCount[key] = missed

                    if (missed <= maxMissingFrames && prevValue != null) {
                        finalDetails[key] = prevValue
                    } else {
                        val penaltyValue = (prevValue ?: 0f) * 0.8f
                        finalDetails[key] = if (penaltyValue > 0.05f) penaltyValue else 0f
                    }
                }
            }
        }

        val activeScores = finalDetails.values.filterNotNull()
        val finalOverallScore = if (activeScores.isNotEmpty()) {
            val averageScore = activeScores.average().toFloat()
            val minScore = activeScores.minOrNull() ?: 0f
            val penaltyWeight = 0.35f
            (averageScore * (1f - penaltyWeight)) + (minScore * penaltyWeight)
        } else {
            0f
        }

        val finalResult = PoseComparisonResult(finalOverallScore, finalDetails)
        previousResult = finalResult
        return finalResult
    }

    private fun getAngleBetweenVectors(
        v1x: Float, v1y: Float, v1z: Float,
        v2x: Float, v2y: Float, v2z: Float
    ): Float {
        val norm1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val norm2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (norm1 == 0f || norm2 == 0f) return 180f

        val dotProduct = (v1x * v2x) + (v1y * v2y) + (v1z * v2z)
        val cosTheta = (dotProduct / (norm1 * norm2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosTheta.toDouble())).toFloat()
    }
}

fun cosineSimilarity(
    poseRef: PredictionObj,
    poseUsr: PredictionObj,
    pose2ImageShape: Size,
    mode: String = "MediaPipe", // "COCO" или "MediaPipe"
    confThreshold: Float = 0.4f,
    maxToleranceDegrees: Float = 45f
): PoseComparisonResult {

    val kpRef = poseRef.keypoints
    val kpUsr = poseUsr.keypoints
    val details = mutableMapOf<String, Float?>()
    val allScores = mutableListOf<Float>()

    val minKeypoints = if (mode == "COCO") 17 else 33
    if (kpRef.xy.size < minKeypoints || kpUsr.xy.size < minKeypoints) {
        return PoseComparisonResult(0f, details)
    }

    /*
    val use3D = kpRef.zn != null && kpRef.zn.isNotEmpty() &&
            kpUsr.zn != null && kpUsr.zn.isNotEmpty()
     */
    val use3D = false

    // Вспомогательная функция: достает Z и масштабирует его до пикселей
    // сейчас 3d не используется
    fun getZ(kp: Keypoints, idx: Int): Float {
        if (!use3D) return 0f
        val zWeight = 0.3f
        return kp.zn!![idx] * pose2ImageShape.width.toFloat()
    }

    val fallbackAnchors: List<Pair<Int, Int>>
    val complexLimbs: Map<String, Triple<Int, Int, Int>>
    val simpleBones: Map<String, Pair<Int, Int>>

    if (mode == "COCO") {
        fallbackAnchors = listOf(
            Pair(11, 12), // Таз
            Pair(5, 6),   // Плечи
            Pair(3, 4),   // Уши
            Pair(1, 2)    // Глаза
        )
        complexLimbs = mapOf(
            "right_hand" to Triple(5, 7, 9),
            "left_hand" to Triple(6, 8, 10),
            "right_leg" to Triple(11, 13, 15),
            "left_leg" to Triple(12, 14, 16)
        )
        simpleBones = mapOf(
            "eyes" to Pair(1, 2),
            "ears" to Pair(3, 4),
            "shoulders" to Pair(5, 6),
            "pelvis" to Pair(11, 12),
            "right_side" to Pair(5, 11),
            "left_side" to Pair(6, 12)
        )
    } else {
        fallbackAnchors = listOf(
            Pair(23, 24), // Таз
            Pair(11, 12), // Плечи
            Pair(7, 8),   // Уши
            Pair(2, 5)    // Глаза
        )
        complexLimbs = mapOf(
            "right_hand" to Triple(11, 13, 15),
            "left_hand" to Triple(12, 14, 16),
            "right_leg" to Triple(23, 25, 27),
            "left_leg" to Triple(24, 26, 28)
        )
        simpleBones = mapOf(
            "eyes" to Pair(2, 5),
            "ears" to Pair(7, 8),
            "shoulders" to Pair(11, 12),
            "pelvis" to Pair(23, 24),
            "right_side" to Pair(11, 23),
            "left_side" to Pair(12, 24)
        )
    }

    fun getAngleBetweenVectors(
        v1x: Float, v1y: Float, v1z: Float,
        v2x: Float, v2y: Float, v2z: Float
    ): Float {
        val norm1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val norm2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (norm1 == 0f || norm2 == 0f) return 180f

        val dotProduct = (v1x * v2x) + (v1y * v2y) + (v1z * v2z)
        val cosTheta = (dotProduct / (norm1 * norm2)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cosTheta.toDouble())).toFloat()
    }

    fun getBoneDirectionDiff(idx1: Int, idx2: Int): Float {
        return getAngleBetweenVectors(
            kpRef.xy[idx2].first - kpRef.xy[idx1].first,
            kpRef.xy[idx2].second - kpRef.xy[idx1].second,
            0f,
            kpUsr.xy[idx2].first - kpUsr.xy[idx1].first,
            kpUsr.xy[idx2].second - kpUsr.xy[idx1].second,
            0f
        )
    }

    fun getJointAngle(kp: Keypoints, idx1: Int, idx2: Int, idx3: Int): Float {
        return getAngleBetweenVectors(
            kp.xy[idx1].first - kp.xy[idx2].first,
            kp.xy[idx1].second - kp.xy[idx2].second,
            getZ(kp, idx1) - getZ(kp, idx2),
            kp.xy[idx3].first - kp.xy[idx2].first,
            kp.xy[idx3].second - kp.xy[idx2].second,
            getZ(kp, idx3) - getZ(kp, idx2)
        )
    }

    val maxGlobalShiftTolerance = 0.15f
    var anchorFound = false
    val aspectRatio = pose2ImageShape.width.toFloat() / pose2ImageShape.height.toFloat()

    // Проверка на правильное расположение модели
    // идем по каскаду по точкам снизу вверх и ищем те, которые есть и на референсе, и на кадре
    // по этой точке проверяем дистанцию между ними
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

        val bone1Diff = getBoneDirectionDiff(idx1, idx2)
        val bone2Diff = getBoneDirectionDiff(idx2, idx3)
        val jointRefAngle = getJointAngle(kpRef, idx1, idx2, idx3)
        val jointUsrAngle = getJointAngle(kpUsr, idx1, idx2, idx3)

        val jointDiff = kotlin.math.abs(jointRefAngle - jointUsrAngle)

        val worstErrorDegrees = maxOf(bone1Diff, bone2Diff, jointDiff)
        val score = (1f - (worstErrorDegrees / maxToleranceDegrees)).coerceIn(0f, 1f)
        Log.d("PoseCheckMain", "$limbName: worstErrorDegrees $worstErrorDegrees, score $score")

        details[limbName] = score
        allScores.add(score)
    }

    for ((boneName, indices) in simpleBones) {
        val (idx1, idx2) = indices

        if (kpRef.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpRef.scores.getOrElse(idx2) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx1) { 0f } < confThreshold ||
            kpUsr.scores.getOrElse(idx2) { 0f } < confThreshold) {

            details[boneName] = null
            continue
        }

        val boneDiff = getBoneDirectionDiff(idx1, idx2)
        val score = (1f - (boneDiff / maxToleranceDegrees)).coerceIn(0f, 1f)

        details[boneName] = score
        allScores.add(score)
    }

    val overallScore = if (allScores.isNotEmpty()) {
        val averageScore = allScores.average().toFloat()
        val minScore = allScores.minOrNull() ?: 0f

        val penaltyWeight = 0.35f
        (averageScore * (1f - penaltyWeight)) + (minScore * penaltyWeight)
    } else {
        0f
    }

    Log.d("PoseCheckMain", "details: $details")
    return PoseComparisonResult(overallScore, details)
}
