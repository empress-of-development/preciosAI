package com.preciosai.photo_capture_plugin

import android.util.Log


data class ZoomData(
    // верхняя и нижняя точек тела из getBodyPoints
    val limitingPoints: Pair<IndexedValue<Pair<Float, Float>?>?, IndexedValue<Pair<Float, Float>?>?>,
    // сверху и снизу накидываем пространство как на реф кадре пропорционально отрезку между точками.
    val addBottomRatio: Float,
    val heightRatio: Float,
    val points: List<Pair<Float, Float>?>,
)


class AutoZoom {
    private val modelType = if (MODEL_TYPE == "MediaPipeEstimator") "MediaPipe" else "COCO"
    private val confidenceThreshold = 0.3f
    private val meanZoomLength = 3

    public var stage = "zoom" // location, kps_comparison
    public val zoomDurationThreshold = 10 // количество кадров для стабилизации зума после того, как он перестал меняться
    public var zoomDurationCount = 0

    public val locationDurationThreshold = 10
    public var locationDurationCount = 0

    public val kpsComparisonDurationThreshold = 10
    public var kpsComparisonDurationCount = 0

    public var tooCloseInd: Int = 0

    public var refZoomData: ZoomData? = null

    public var zoomLevelList = mutableListOf<Float?>()

    public var isPortrait: Boolean = false

    private fun avg(obj: PredictionObj, kpsIdxs: List<Int>): Pair<Float, Float>? {
        // TODO Сделать несколько объектов!
        var xyn = obj.keypoints.xyn
        if (obj.keypoints.xynCorrected != null) xyn = obj.keypoints.xynCorrected!!

        val mean = xyn.slice(kpsIdxs)
            .zip(obj.keypoints.scores.slice(kpsIdxs))
            .filter { (_, v) -> v > confidenceThreshold }
            .map { (x, _) -> x }
            .let { filtered ->
                val n = filtered.size
                if (n > 0) {
                    val (sx, sy) = filtered.fold(Pair(0f, 0f)) { acc, p ->
                        Pair(acc.first + p.first, acc.second + p.second)
                    }
                    Pair(sx / n, sy / n)
                } else null
            }
        return mean
    }

    private fun getBodyPoints(obj: PredictionObj): List<Pair<Float, Float>?> {
        // Предполагаем, что голова всегда есть в кадре
        // элементы списка - либо точки, либо null
        if (modelType == "COCO") {
            return listOf(
                avg(obj, listOf(1, 2)), //eyes
                avg(obj, listOf(3, 4)), //ears
                //if (obj.keypoints.scores[0] > 0.45) res.keypoints.xyn[0] else null, //nose
                avg(obj, listOf(0)), //nose
                avg(obj, listOf(5, 6)), //shoulders
                avg(obj, listOf(11, 12)), //pelvis
                //avg(obj, listOf(13, 14)), //knees
                //avg(obj, listOf(15, 16)), //feet
            )
        } else {
            return listOf(
                avg(obj, listOf(5, 2)), //eyes
                avg(obj, listOf(8, 7)), //ears
                //if (obj.keypoints.scores[0] > 0.45) res.keypoints.xyn[0] else null, //nose
                avg(obj, listOf(0)), //nose
                avg(obj, listOf(9, 10)), //mouth
                avg(obj, listOf(12, 11)), //shoulders
                avg(obj, listOf(24, 23)), //pelvis
                //avg(obj, listOf(13, 14)), //knees
                //avg(obj, listOf(15, 16)), //feet
            )
        }
    }

    private fun getZoomData(obj: PredictionObj?, isReference: Boolean = false): ZoomData? {
        if (obj == null || obj.keypoints == null) return null
        var bboxXywhn = obj.bbox.xywhn
        if (obj.bbox.xywhnCorrected != null) bboxXywhn = obj.bbox.xywhnCorrected!!

        val points = getBodyPoints(obj)
        val filtered = points.withIndex().filter { it.value != null }
        if (filtered.size < 2) return null
        val limitingPoints = filtered.minByOrNull { it.value!!.second } to filtered.maxByOrNull { it.value!!.second }

        // bodyHeight - высота тела от высшей точки бокса до нижней из ключевых точек
        val bodyHeight = limitingPoints.second!!.value!!.second - bboxXywhn.top // limitingPoints.first!!.value!!.second - надо бы глаза тоже править
        // addBottomRatio - нижняя часть между боксом и нижней ключевой точкой
        val bboxHeight = if (limitingPoints.second!!.index < 5) 1 - bboxXywhn.top else bboxXywhn.height()
        var addBottomRatio = 1 - bodyHeight / bboxHeight

        if (isReference && limitingPoints.second!!.index < 5) {
            isPortrait = true
        }

        return ZoomData(
            limitingPoints = limitingPoints,
            addBottomRatio = addBottomRatio,
            heightRatio = bboxXywhn.height(),
            points = points,
        )
    }

    public fun getRefZoomData(refObj: PredictionObj?) {
        // Получаем необходимые пропорции человека по референсному кадру
        refZoomData = getZoomData(refObj, isReference = true)
    }

    public fun getZoomLevel(obj: PredictionObj): Float? {
        // Получаем уровень зума по текущему кадру с камеры

        val currentZoomData = getZoomData(obj)
        if (refZoomData == null || currentZoomData == null) return 1f

        // TODO переделать бы
        // Предполагаем, что голова всегда есть в кадре и ноги не задирают!!! Человек просто вертикально стоит
        if (currentZoomData!!.limitingPoints.second!!.index < refZoomData!!.limitingPoints.second!!.index) {
            // Слишком близко!!! Самая низкая точка на референсе ниже, чем на кадре с камеры
            if (!zoomLevelList.isEmpty() && zoomLevelList.last() != null) zoomLevelList.clear()

            if (zoomLevelList.size >= meanZoomLength) {
                return null
            } else {
                zoomLevelList.add(null)
                return 1f
            }
        } else {
            var bodyHeight: Float = 0f
            val bboxHeight = if (currentZoomData!!.limitingPoints.second!!.index < 5)
                1 - currentZoomData!!.limitingPoints.first!!.value!!.second else obj.bbox.xywhn.height()

            if (currentZoomData!!.limitingPoints.second!!.index == refZoomData!!.limitingPoints.second!!.index) {
                bodyHeight = currentZoomData!!.limitingPoints.second!!.value!!.second -
                        obj.bbox.xywhn.top + refZoomData!!.addBottomRatio * bboxHeight
            } else {
                // то есть в кадре видно больше точек, чем на референсе
                val p = refZoomData!!.limitingPoints.second!!
                if (currentZoomData!!.points[p.index] != null) {
                    bodyHeight = currentZoomData!!.points[p.index]!!.second -
                            obj.bbox.xywhn.top + refZoomData!!.addBottomRatio * bboxHeight
                }
            }

            if (bodyHeight == 0f) return 1f

            if (!zoomLevelList.isEmpty() && zoomLevelList.last() == null) zoomLevelList.clear()

            val zoomValue = refZoomData!!.heightRatio / bodyHeight
            zoomLevelList.add(zoomValue)
            if (zoomLevelList.size >= meanZoomLength) {
                val zoomValueRes = zoomLevelList.filterNotNull().average().toFloat()
                zoomLevelList.clear()
                Log.d("AutoZoom", "zoomValueRes: $zoomValueRes, zoomLevelList: $zoomLevelList")
                return zoomValueRes
            } else {
                return 1f
            }
        }
    }
}
