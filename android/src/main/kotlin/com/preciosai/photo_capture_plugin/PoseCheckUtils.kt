
package com.preciosai.photo_capture_plugin

import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max
import kotlin.math.pow
import android.util.Log
import kotlin.math.exp
import android.graphics.RectF
import org.opencv.core.Size


fun iou(rect1: RectF, rect2: RectF): Float {
    // координаты пересечения
    val overlapLeft = maxOf(rect1.left, rect2.left)
    val overlapTop = maxOf(rect1.top, rect2.top)
    val overlapRight = minOf(rect1.right, rect2.right)
    val overlapBottom = minOf(rect1.bottom, rect2.bottom)

    // проверка пересечения
    if (overlapRight - overlapLeft <= 0f || overlapBottom - overlapTop <= 0f) {
        return 0f
    }

    val intersection = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
    val area1 = rect1.width() * rect1.height()
    val area2 = rect2.width() * rect2.height()
    val union = area1 + area2 - intersection

    return intersection / union
}


fun comparePoses(poseComparisonMode: String, pose1: PredictionObj, pose2: PredictionObj, pose2ImageShape: Size): Float? {
    var compareRes: Float? = null
    if (poseComparisonMode == "simple") {
        compareRes = euclideanDistance(pose1, pose2)
    } else if (poseComparisonMode == "OKS") {
        compareRes = objectKeypointSimilarity(pose1, pose2, pose2ImageShape)
    } else if (poseComparisonMode == "PDJ") {
        compareRes = percentageOfDetectedJoints(pose1, pose2)
    }
    return compareRes
}

fun euclideanDistance(
    pose1: PredictionObj,
    pose2: PredictionObj,
    excludeIndexes: List<Int> = (17..133).toList(), //emptyList(), //TODO часть точек не учитывается
    scoreThreshold: Float = 0.4f
): Float? {
    // Euclidean distance for poses as similarity measure
    if (pose1 == null || pose2 == null || pose1.keypoints == null || pose2.keypoints == null) {
        return null
    }

    var sumSq = 0f
    var validCount = 0

    for (i in 0 until pose1.keypoints.xyn.size) {
        if (i in excludeIndexes) continue
        // надо бы штрафовать точки, которые есть в одной позе, но которых нет в другой
        if (pose1.keypoints.scores[i] > scoreThreshold &&
            pose2.keypoints.scores[i] > scoreThreshold
        ) {
            val (x1, y1) = pose1.keypoints.xy[i]
            val (x2, y2) = pose2.keypoints.xy[i]

            val nx1 = (x1 - pose1.bbox.xywh.left) / pose1.bbox.xywh.width()
            val ny1 = (y1 - pose1.bbox.xywh.top) / pose1.bbox.xywh.height()
            val nx2 = (x2 - pose2.bbox.xywh.left) / pose2.bbox.xywh.width()
            val ny2 = (y2 - pose2.bbox.xywh.top) / pose2.bbox.xywh.height()

            val dx = nx1 - nx2
            val dy = ny1 - ny2
            sumSq += dx * dx + dy * dy
            validCount++

        }
    }
    return 1f - sqrt(sumSq)
}


val COCO_SIGMAS = floatArrayOf(
    0.026f, 0.025f, 0.025f, 0.035f, 0.035f,
    0.079f, 0.079f, 0.072f, 0.072f, 0.062f,
    0.062f, 0.107f, 0.107f, 0.087f, 0.087f,
    0.089f, 0.089f
)

fun objectKeypointSimilarity(
    pose1: PredictionObj,
    pose2: PredictionObj,
    pose2ImageShape: Size,
    sigmas: FloatArray = COCO_SIGMAS,
    excludeIndexes: List<Int> = (17..133).toList(), // TODO для использования остальных точек нужно им сигмы задать
    scoreThreshold: Float = 0.4f,
): Float? {
    // Object Keypoint Similarity (OKS)
    if (pose1 == null || pose2 == null || pose1.keypoints == null || pose2.keypoints == null) {
        return null
    }

    val n = min(pose1.keypoints.xy.size, sigmas.size)
    val area = pose1.bbox.xywhn.width() * pose2ImageShape.width.toFloat() * pose1.bbox.xywhn.height() * pose2ImageShape.height.toFloat()
    var oksSum = 0f
    var validCount = 0

    for (i in 0 until n) {
        if (i !in excludeIndexes && pose1.keypoints.scores[i] > scoreThreshold) {
            val k = sigmas[i]
            val e = if (pose2.keypoints.scores[i] > scoreThreshold) {
                val (x1, y1) = Pair(
                    pose1.keypoints.xyn[i].first * pose2ImageShape.width.toFloat(),
                    pose1.keypoints.xyn[i].second * pose2ImageShape.height.toFloat()
                )
                val (x2, y2) = pose2.keypoints.xy[i]
                val (dx, dy) = Pair(x1 - x2, y1 - y2)
                val d2 = dx * dx + dy * dy
                exp(-d2 / (2f * area * k * k))
            } else {
                0f
            }

            oksSum += e
            validCount++
        }
    }

    return if (validCount > 0) oksSum / validCount else 0f
}



fun percentageOfDetectedJoints(
    pose1: PredictionObj,
    pose2: PredictionObj,
    excludeIndexes: List<Int> = (17..133).toList(),
    scoreThreshold: Float = 0.4f,
    alpha: Float = 0.05f
): Float? {
    // Percentage of Detected Joints (PDJ)
    if (pose1 == null || pose2 == null || pose1.keypoints == null || pose2.keypoints == null) {
        return null
    }

    val referenceLength = 1
    val threshold = alpha * referenceLength
    var refJoints = 0
    var detectedJoints = 0

    for (i in 0 until pose1.keypoints.xy.size) {
        if (i !in excludeIndexes && pose1.keypoints.scores[i] > scoreThreshold) {
            refJoints++
            if (pose2.keypoints.scores[i] > scoreThreshold) {
                val (x1, y1) = pose1.keypoints.xy[i]
                val (x2, y2) = pose2.keypoints.xy[i]

                val nx1 = (x1 - pose1.bbox.xywh.left) / pose1.bbox.xywh.width()
                val ny1 = (y1 - pose1.bbox.xywh.top) / pose1.bbox.xywh.height()
                val nx2 = (x2 - pose2.bbox.xywh.left) / pose2.bbox.xywh.width()
                val ny2 = (y2 - pose2.bbox.xywh.top) / pose2.bbox.xywh.height()

                val dx = nx1 - nx2
                val dy = ny1 - ny2

                val distance = sqrt(dx * dx + dy * dy)

                if (distance <= threshold) {
                    detectedJoints++
                }
            }
        }
    }
    Log.d("PoseCheckUtils", "detectedJoints $detectedJoints, refJoints $refJoints")
    return detectedJoints.toFloat() / refJoints.toFloat()
}
