package com.preciosai.photo_capture_plugin

import android.graphics.RectF
import org.opencv.core.Size


data class InstanceObj(
    val imageShape: Size,
    val objects: List<PredictionObj> = emptyList(),
    val speed: Double,
    val fps: Double? = null
)


data class PredictionObj(
    val bbox: BBox,
    val keypoints: Keypoints,
    val label: String,
    val score: Float
)


data class BBox(
    var clsIndex: Int,
    var label: String,
    var score: Float,
    val xywh: RectF,
    val xywhn: RectF
)


data class Keypoints(
    var xyn: List<Pair<Float, Float>>,
    var xy: List<Pair<Float, Float>>,
    var scores: List<Float>
)
