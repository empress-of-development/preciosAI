package com.preciosai.photo_capture_plugin

import android.animation.ValueAnimator
import com.airbnb.lottie.LottieAnimationView
import android.content.Context
import android.view.ContextThemeWrapper
import com.airbnb.lottie.RenderMode
import android.widget.FrameLayout
import android.view.View
import java.io.File
import com.airbnb.lottie.LottieCompositionFactory


data class AnimationState(
    var visible: Boolean = false,
    var animationOffset: Float = 0f,
    val duration: Long = 1000L,
    val limits: Pair<Float, Float>,
)

class AnimationController(
    var state: AnimationState,
    val requestInvalidate: () -> Unit,
    private val post: ((() -> Unit) -> Unit)
) {

    public val animator = ValueAnimator.ofFloat(state.limits.first, state.limits.second).apply {
        duration = state.duration
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            state.animationOffset = it.animatedValue as Float
            requestInvalidate()
        }
    }

    fun setAnimationVisible(visible: Boolean) {
        setVisible(
            visible = visible,
            startAnimation = {
                post {
                    if (!animator.isStarted) animator.start()
                }
            },
            stopAnimation = {
                post {
                    animator.cancel()
                    state.animationOffset = 0f
                    requestInvalidate()
                }
            }
        )
    }

    fun setVisible(
        visible: Boolean,
        startAnimation: () -> Unit,
        stopAnimation: () -> Unit
    ) {
        if (state.visible == visible) return

        state.visible = visible

        if (visible) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }
}


class LottieAnimation(
    private val context: Context,
    private val fileName: String
) {
    val lottieView = LottieAnimationView(
        ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
    ).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        repeatCount = 0
        progress = 0f
        pauseAnimation()
        visibility = View.GONE
        setRenderMode(RenderMode.HARDWARE)
    }

    fun loadAndPlay(autoPlay: Boolean = true, speed: Float = 1f) {
        try {
            val fullPath = "flutter_assets/$fileName"
            val inputStream = context.assets.open(fullPath)

            LottieCompositionFactory.fromJsonInputStream(inputStream, fullPath)
                .addListener { composition ->
                    lottieView.setComposition(composition)

                    if (autoPlay) {
                        lottieView.visibility = View.VISIBLE
                        lottieView.progress = 0f
                        lottieView.setSpeed(speed)
                        lottieView.playAnimation()
                    }
                }
                .addFailureListener { throwable ->
                    throwable.printStackTrace()
                    lottieView.visibility = View.GONE
                }

        } catch (e: Exception) {
            e.printStackTrace()
            lottieView.visibility = View.GONE
        }
    }

    fun showOverlay(speed: Float = 1f) {
        if (!lottieView.isAnimating) {
            lottieView.visibility = View.VISIBLE
            lottieView.progress = 0f
            lottieView.setSpeed(speed)
            lottieView.playAnimation()
        }
    }

    fun hideOverlay() {
        if (lottieView.isAnimating) {
            lottieView.cancelAnimation()
        }
        lottieView.visibility = View.GONE
    }
}
