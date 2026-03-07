package com.preciosai.photo_capture_plugin

import android.content.Context
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.common.BinaryMessenger


class CameraPlatformView(
    private val context: Context,
    private val viewId: Int,
    creationParams: Map<String?, Any?>?,
    private val methodChannel: MethodChannel?,
    private val factory: CameraPlatformViewFactory,
    private val messenger: BinaryMessenger,
) : PlatformView, MethodChannel.MethodCallHandler {

    private val TAG = "CameraPlatformView"

    private val cameraView: CameraView = CameraView(context, methodChannel, messenger)
    val cameraViewInstance: CameraView get() = cameraView

    private val viewUniqueId: String = (creationParams?.get("viewId") as? String)
        ?: viewId.toString().also {
            Log.w(TAG, "[$viewId init] Using platform int viewId '$it' as fallback")
        }

    init {
        Log.d(TAG, "[$viewId init] CameraPlatformView initialized with viewUniqueId: $viewUniqueId")

        methodChannel?.setMethodCallHandler(this)

        cameraView.setupThrottling()
        cameraView.initCamera()

        if (context is LifecycleOwner) {
            Log.d(TAG, "context is LifecycleOwner")

            cameraView.onLifecycleOwnerAvailable(context)
        }

        try {
            cameraView.setModel()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing CameraView", e)
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "setModel" -> {
                    cameraView.setModel { success ->
                        if (success) result.success(null)
                        else result.error("MODEL_NOT_FOUND", "Failed to load model", null)
                    }
                }
                "switchCamera" -> {
                    cameraView.switchCamera()
                    result.success(null)
                }
                "stop" -> {
                    cameraView.stop()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling method call: ${call.method}", e)
            result.error("METHOD_CALL_ERROR", e.message, null)
        }
    }

    override fun getView(): View = cameraView

    override fun dispose() {
        Log.d(TAG, "Disposing CameraPlatformView for viewId: $viewId")

        try {
            cameraView.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during CameraView disposal", e)
        }

        methodChannel?.setMethodCallHandler(null)
        factory.onPlatformViewDisposed(viewId)

        Log.d(TAG, "CameraPlatformView disposed successfully")
    }
}
