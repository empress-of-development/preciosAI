package com.preciosai.photo_capture_plugin

import android.graphics.*
import kotlin.math.max
import android.util.Log
import kotlin.arrayOf

data class OverlayState(
    val result: InstanceObj?,
    val refDetectionResult: InstanceObj?,
    val isFrontCamera: Boolean,
    val showPulseRect: Boolean,
    val rectAlpha: Int,
    val showBackArrow: Boolean,
    val captureRequested: Boolean,
    val arrowAnimationOffset: Float,
    val poseComparisonDetails: Map<String, Float?>? = null,
    val visualizationMode: String = "skeleton+capsules",
    val isPortrait: Boolean = false
)

data class PoseTopology(
    val skeletonUpperBody: Array<IntArray>,
    val skeletonUpperBodyIndexes: IntArray,
    val skeletonLowerBody: Array<IntArray>,
    val skeletonLowerBodyIndexes: IntArray,
    val skeletonExcludedIndexes: IntArray,
    val skeletonPartially: Array<IntArray>
)

class OverlayRenderer @JvmOverloads constructor(private val poseMode: String) {

    companion object {

        private const val TAG = "CameraView - DrawViewUtils"

        private const val confidenceThreshold = 0.3f

        private const val BOX_CORNER_RADIUS = 12f

        private val poseTopologies: Map<String, PoseTopology> = mapOf(
            "COCO" to PoseTopology(
                skeletonUpperBody = arrayOf(
                    intArrayOf(8, 10), intArrayOf(6, 8), intArrayOf(5, 6), intArrayOf(5, 7), intArrayOf(7, 9)
                ),
                skeletonUpperBodyIndexes = intArrayOf(10, 8, 6, 5, 7, 9),

                skeletonLowerBody = arrayOf(
                    intArrayOf(16, 14), intArrayOf(14, 12), intArrayOf(11, 12), intArrayOf(13, 11), intArrayOf(15, 13)
                ),
                skeletonLowerBodyIndexes = intArrayOf(16, 14, 12, 11, 13, 15),

                skeletonExcludedIndexes = intArrayOf(17, 18, 19, 20, 21, 22),
                skeletonPartially = arrayOf(
                    intArrayOf(5, 11), intArrayOf(6, 12), // sides of the body
                    intArrayOf(1, 2), intArrayOf(0, 1), intArrayOf(0, 2), // eyes and nose
                    intArrayOf(1, 3), intArrayOf(2, 4), // ears // intArrayOf(3, 5), intArrayOf(4, 6),
                    intArrayOf(15, 17), intArrayOf(15, 18), intArrayOf(15, 19), intArrayOf(16, 20), intArrayOf(16, 21), intArrayOf(16, 22), // feets
                    intArrayOf(91, 92), intArrayOf(92, 93), intArrayOf(93, 94), intArrayOf(94, 95), intArrayOf(91, 96), // palms
                    intArrayOf(96, 97), intArrayOf(97, 98),
                    intArrayOf(98, 99), intArrayOf(91, 100), intArrayOf(100, 101), intArrayOf(101, 102), intArrayOf(102, 103),
                    intArrayOf(91, 104), intArrayOf(104, 105), intArrayOf(105, 106), intArrayOf(106, 107), intArrayOf(91, 108),
                    intArrayOf(108, 109), intArrayOf(109, 110), intArrayOf(110, 111), intArrayOf(112, 113), intArrayOf(113, 114),
                    intArrayOf(114, 115), intArrayOf(115, 116), intArrayOf(112, 117), intArrayOf(117, 118), intArrayOf(118, 119),
                    intArrayOf(119, 120), intArrayOf(112, 121), intArrayOf(121, 122), intArrayOf(122, 123), intArrayOf(123, 124),
                    intArrayOf(112, 125), intArrayOf(125, 126), intArrayOf(126, 127), intArrayOf(127, 128), intArrayOf(112, 129),
                    intArrayOf(129, 130), intArrayOf(130, 131), intArrayOf(131, 132)
                )
            ),
            "MediaPipe" to PoseTopology(
                skeletonUpperBody = arrayOf(
                    intArrayOf(14, 16), intArrayOf(12, 14), intArrayOf(11, 12), intArrayOf(11, 13), intArrayOf(13, 15)
                ),
                skeletonUpperBodyIndexes = intArrayOf(16, 14, 12, 11, 13, 15),

                skeletonLowerBody = arrayOf(
                    intArrayOf(28, 26), intArrayOf(26, 24), intArrayOf(23, 24), intArrayOf(25, 23), intArrayOf(27, 25)
                ),
                skeletonLowerBodyIndexes = intArrayOf(28, 26, 24, 23, 25, 27),

                skeletonExcludedIndexes = intArrayOf(),
                skeletonPartially = arrayOf(
                    intArrayOf(12, 24), intArrayOf(11, 23), // sides of the body
                    intArrayOf(8, 5), intArrayOf(5, 0), intArrayOf(0, 2), intArrayOf(2, 7), // eyes and nose
                    intArrayOf(10, 9), // mouth
                    intArrayOf(28, 32), intArrayOf(32, 30), intArrayOf(30, 28), // left feet
                    intArrayOf(27, 29), intArrayOf(29, 31), intArrayOf(31, 27), // right feet
                    intArrayOf(16, 22), intArrayOf(16, 18), intArrayOf(18, 20), intArrayOf(16, 20),  // left palm
                    intArrayOf(15, 21), intArrayOf(15, 17), intArrayOf(17, 19), intArrayOf(15, 19),  // right palm
                )

            )
        )
        private val boneColors = listOf(Color.parseColor("#B3B19CD9"), Color.parseColor("#B3B4F9D7"))

    }

    private val paint = Paint().apply { isAntiAlias = true }

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3B4F9D7")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(12f, 12f), 0f)
        alpha = 160
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3B4F9D7")
        style = Paint.Style.FILL
    }

    private val bonePaintCurved = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3B4F9D7")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = CornerPathEffect(70f) // радиус скругления
        alpha = 160
    }

    private val currentPoseIndexes: PoseTopology = poseTopologies[poseMode]!!
    private val capsulePoseRenderer = CapsulePoseRenderer(poseMode)

    private fun captureDraw(canvas: Canvas, width: Float, height: Float) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(200, 255, 255, 255)
            textSize = 64f
            typeface = Typeface.MONOSPACE
        }

        val bounds = Rect()
        var text = "Adjust the frame and"
        textPaint.getTextBounds(text, 0, text.length, bounds)

        var x = (width - bounds.width()) / 2f
        var y = (height + bounds.height()) / 6.5f

        canvas.drawText(text, x, y, textPaint)

        val bounds_1 = Rect()
        text = "do not move the camera!"
        textPaint.getTextBounds(text, 0, text.length, bounds_1)

        x = (width - bounds_1.width()) / 2f
        y = (height + bounds_1.height()) / 6.5f + bounds.height()

        canvas.drawText(text, x, y, textPaint)

    }

    private fun drawLimb(canvas: Canvas, joints: Array<PointF?>, jointsIndexes: IntArray): Boolean {
        val path = Path()
        var idx = 0
        var p = joints.getOrNull(jointsIndexes[idx])
        while (p == null) {
            idx += 1
            if (idx >= jointsIndexes.size) return false
            p = joints.getOrNull(jointsIndexes[idx])
        }

        path.moveTo(joints[jointsIndexes[idx]]!!.x, joints[jointsIndexes[idx]]!!.y)
        idx += 1
        for (i in idx until jointsIndexes.size) {
            p = joints.getOrNull(jointsIndexes[i])
            // нет линии плеч или линии таза => не рисуем изогнутые линии, рисуем чисто суставы
            if (i in arrayOf(2, 3) && p == null) return false
            if (p != null) path.lineTo(p.x, p.y)
        }
        canvas.drawPath(path, bonePaintCurved)
        return true
    }

    private fun drawVerticalBackArrow(canvas: Canvas, arrowAnimationOffset: Float, width: Float, height: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Начало и конец движения стрелки
        val startY = h * 0.75f
        val endY = h

        // Смещение по анимации (0 -> 1)
        val currentY = startY + (endY - startY) * arrowAnimationOffset

        val topWidth = w * 0.08f
        val bottomWidth = w * 0.18f
        val arrowWidth = topWidth + (bottomWidth - topWidth) * arrowAnimationOffset

        val headHeight = arrowWidth * 1.2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        val startColor = Color.argb(200, 63, 54, 145)
        val endColor = Color.argb(80, 255, 255, 255)
        paint.shader = LinearGradient(
            w/2, currentY - topWidth,
            w/2, currentY + headHeight,
            intArrayOf(startColor, endColor),
            null,
            Shader.TileMode.CLAMP
        )

        // Тело стрелки - трапеция
        val path = Path()
        path.moveTo(w/2 - topWidth/2, currentY - topWidth)
        path.lineTo(w/2 + topWidth/2, currentY - topWidth)
        path.lineTo(w/2 + arrowWidth/2, currentY)
        path.lineTo(w/2 - arrowWidth/2, currentY)
        path.close()
        canvas.drawPath(path, paint)

        // Голова стрелки - треугольник
        val headPath = Path()
        headPath.moveTo(w/2 - arrowWidth, currentY)
        headPath.lineTo(w/2 + arrowWidth, currentY)
        headPath.lineTo(w/2, currentY + headHeight)
        headPath.close()
        canvas.drawPath(headPath, paint)
    }

    public fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        state: OverlayState
    ) {

        if (state.captureRequested) {
            captureDraw(canvas, width, height)
            return
        }

        if (state.result == null || state.refDetectionResult == null) return

        val resWidth: Float = state.result.imageShape.width.toFloat()
        val resHeight: Float = state.result.imageShape.height.toFloat()

        var frameWidth: Float = width.toFloat()
        var frameHeight: Float = height.toFloat()
        var topBorder = 0f
        var leftBorder = 0f
        if (frameHeight > frameWidth) {
            frameHeight = width.toFloat() * resHeight / resWidth
            topBorder = (height.toFloat() - frameHeight) / 2f
        } else {
            frameWidth = height.toFloat() * resWidth / resHeight
            leftBorder = (width.toFloat() - frameWidth) / 2f
        }

        val scale: Float = max(frameWidth / resWidth, frameHeight / resHeight)

        val dx = (frameWidth - resWidth * scale) / 2f
        val dy = (frameHeight - resHeight * scale) / 2f

        // TODO нужно ли рисовать бокс
        if (state.showPulseRect && state.refDetectionResult != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(state.rectAlpha, 0, 0, 255) // синий с меняющейся прозрачностью
            }
            for (refPred in state.refDetectionResult!!.objects) {
                if (refPred.bbox.xywhnCorrected == null) {
                    canvas.drawRect(
                        leftBorder + refPred.bbox.xywhn.left * resWidth * scale + dx,
                        topBorder + refPred.bbox.xywhn.top * resHeight * scale + dy,
                        leftBorder + refPred.bbox.xywhn.right * resWidth * scale + dx,
                        topBorder + refPred.bbox.xywhn.bottom * resHeight * scale + dy,
                        paint
                    )
                } else {
                    canvas.drawRect(
                        leftBorder + refPred.bbox.xywhnCorrected!!.left * resWidth * scale + dx,
                        topBorder + refPred.bbox.xywhnCorrected!!.top * resHeight * scale + dy,
                        leftBorder + refPred.bbox.xywhnCorrected!!.right * resWidth * scale + dx,
                        topBorder + refPred.bbox.xywhnCorrected!!.bottom * resHeight * scale + dy,
                        paint
                    )
                }
            }
        }

        val resList = state.result.objects + (state.refDetectionResult!!.objects ?: emptyList())

        // Keypoints & skeleton

        if (state.visualizationMode != "empty") {
            for ((idx, person) in resList.withIndex()) {
                var currColor = Color.parseColor("#D50000")
                if (idx < boneColors.size) {
                    currColor = boneColors[idx]
                }
                bonePaint.color = currColor
                bonePaintCurved.color = currColor
                jointPaint.color = currColor

                val points = arrayOfNulls<PointF>(person.keypoints.xyn.size)
                for (i in person.keypoints.xyn.indices) {
                    if (person.keypoints.scores[i] > confidenceThreshold && i !in currentPoseIndexes.skeletonExcludedIndexes) {
                        var pxCam = person.keypoints.xyn[i].first * resWidth
                        var pyCam = person.keypoints.xyn[i].second * resHeight

                        if (person.keypoints.xynCorrected != null) {
                            pxCam = person.keypoints.xynCorrected!![i].first * resWidth
                            pyCam = person.keypoints.xynCorrected!![i].second * resHeight
                        }

                        var px = leftBorder + pxCam * scale + dx
                        var py = topBorder + pyCam * scale + dy

                        if (state.visualizationMode in listOf("skeleton", "skeleton+capsules")) {
                            canvas.drawCircle(px, py, 8f, jointPaint)
                        }
                        points[i] = PointF(px, py)
                    }
                }

                if (state.visualizationMode in listOf("skeleton", "skeleton+capsules")) {
                    var skeletonJoints = currentPoseIndexes.skeletonPartially
                    var drawRes = drawLimb(canvas, points, currentPoseIndexes.skeletonUpperBodyIndexes)
                    if (!drawRes) skeletonJoints = currentPoseIndexes.skeletonPartially.plus(currentPoseIndexes.skeletonUpperBody)

                    drawRes = drawLimb(canvas, points, currentPoseIndexes.skeletonLowerBodyIndexes)
                    if (!drawRes) skeletonJoints = currentPoseIndexes.skeletonPartially.plus(currentPoseIndexes.skeletonLowerBody)

                    // Skeleton connection
                    for ((idx, bone) in skeletonJoints.withIndex()) {
                        val p1 = points.getOrNull(bone[0])
                        val p2 = points.getOrNull(bone[1])
                        if (p1 != null && p2 != null) {
                            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, bonePaint)
                        }
                    }
                }

                if (
                    idx != resList.size - 1 &&
                    state.visualizationMode in listOf("capsules", "skeleton+capsules") &&
                    state.poseComparisonDetails != null
                ) {
                    capsulePoseRenderer.drawPose(canvas, points, state.poseComparisonDetails)
                }
            }
        }

        if (!state.isPortrait) {
            for (person in state.result.objects) {
                // BBox
                var left = leftBorder + person.bbox.xywh.left * scale + dx
                var top = topBorder + person.bbox.xywh.top * scale + dy
                var right = leftBorder + person.bbox.xywh.right * scale + dx
                var bottom = topBorder + person.bbox.xywh.bottom * scale + dy

                paint.color = Color.argb(200, 63, 54, 145)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f
                paint.pathEffect = DashPathEffect(floatArrayOf(30f, 30f), 0f)
                canvas.drawRoundRect(
                    left, top, right, bottom,
                    BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                    paint
                )
            }
        }

        if (state.showBackArrow) {
            drawVerticalBackArrow(canvas, state.arrowAnimationOffset, width, height)
        }
    }

}
