package com.preciosai.photo_capture_plugin

import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory


class CameraPlatformViewFactory(
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    private val TAG = "CameraPlatformViewFactory"
    private var activity: Activity? = null
    internal val activeViews = mutableMapOf<Int, CameraPlatformView>()

    fun setActivity(activity: Activity?) {
        this.activity = activity
        Log.d(TAG, "Activity set: ${activity?.javaClass?.simpleName}")
    }
    
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as? Map<String?, Any?>
        
        val effectiveContext = activity ?: context

        val viewUniqueId = (creationParams?.get("viewId") as? String)
            ?: viewId.toString().also {
                Log.w(TAG, "Using platform viewId '$it' as fallback for viewUniqueId because Dart 'viewId' was null or not a String.")
            }

        val controlChannelName = "com.preciosai.photo_capture_plugin/controlChannel_$viewUniqueId"
        val methodChannel = MethodChannel(messenger, controlChannelName)

        val platformView = CameraPlatformView(
            effectiveContext,
            viewId,
            creationParams,
            methodChannel,
            this
        ).apply {
            methodChannel.setMethodCallHandler(this)
        }

        activeViews[viewId] = platformView
        Log.d(TAG, "Created CameraPlatformView with viewId=$viewId, channel=$controlChannelName")
        return platformView
    }
    
    internal fun onPlatformViewDisposed(viewId: Int) {
        activeViews.remove(viewId)
        Log.d(TAG, "CameraPlatformView for viewId $viewId disposed and removed from factory")
    }
    
    fun dispose() {
        activeViews.clear()
        Log.d(TAG, "CameraPlatformViewFactory disposed.")
    }
}