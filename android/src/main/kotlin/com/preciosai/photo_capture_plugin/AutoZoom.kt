package com.preciosai.photo_capture_plugin

import android.util.Log


data class ZoomData(
    // верхняя и нижняя точек тела из getBodyPoints
    val limitingPoints: Pair<IndexedValue<Pair<Float, Float>?>?, IndexedValue<Pair<Float, Float>?>?>,
    // сверху и снизу накидываем пространство как на реф кадре пропорционально отрезку между точками.
    val addBottomRatio: Float,
    val heightRatio: Float,
)


class AutoZoom {
    private var confidenceThreshold = 0.2f
    private var meanZoomLength = 3

    public var stage = "zoom" // location, kps_comparison
    public var zoomDurationThreshold = 10 // количество кадров для стабилизации зума после того, как он перестал меняться
    public var zoomDurationCount = 0

    public var locationDurationThreshold = 10
    public var locationDurationCount = 0

    public var kpsComparisonDurationThreshold = 10
    public var kpsComparisonDurationCount = 0

    public var tooCloseInd: Int = 0

    public var refZoomData: ZoomData? = null

    public var zoomLevelList = mutableListOf<Float?>()

    private fun avg(obj: PredictionObj, kpsIdxs: List<Int>): Pair<Float, Float>? {
        // TODO Сделать несколько объектов!
        val mean = obj.keypoints.xyn.slice(kpsIdxs)
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
    }

    private fun getZoomData(obj: PredictionObj?): ZoomData? {
        if (obj == null || obj.keypoints == null) return null

        var filtered = getBodyPoints(obj).withIndex().filter { it.value != null }
        if (filtered.size < 2) return null
        val limitingPoints = filtered.minByOrNull { it.value!!.second } to filtered.maxByOrNull { it.value!!.second }

        // bodyHeight - высота тела от высшей точки бокса до нижней из ключевых точек
        val bodyHeight = limitingPoints.second!!.value!!.second - obj.bbox.xywhn.top // limitingPoints.first!!.value!!.second - надо бы глаза тоже править
        // addBottomRatio - нижняя часть между боксом и нижней ключевой точкой
        val addBottomRatio = 1 - bodyHeight / obj.bbox.xywhn.height()

        val heightRatio = obj.bbox.xywhn.height()

        return ZoomData(
            limitingPoints=limitingPoints,
            addBottomRatio=addBottomRatio,
            heightRatio=heightRatio,
        )
    }

    public fun getRefZoomData(refObj: PredictionObj?) {
        // Получаем необходимые пропорции человека по референсному кадру
        refZoomData = getZoomData(refObj)
    }

    public fun getZoomLevel(obj: PredictionObj, currentZoomLevel: Float = 1f): Float? {
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
            val bodyHeight = currentZoomData.limitingPoints.second!!.value!!.second -
                    obj.bbox.xywhn.top + refZoomData!!.addBottomRatio * obj.bbox.xywhn.height()

            Log.d("AutoZoom", "refHeightRatio: ${refZoomData!!.heightRatio}, bodyHeight: $bodyHeight")

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
