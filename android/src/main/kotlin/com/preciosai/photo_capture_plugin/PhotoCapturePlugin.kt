package com.preciosai.photo_capture_plugin

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import kotlinx.serialization.json.Json


class PhotoCapturePlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler,
    PluginRegistry.RequestPermissionsResultListener {

    private val TAG = "PhotoCapturePlugin"
    private lateinit var methodChannel: MethodChannel
    private lateinit var applicationContext: android.content.Context
    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null
    private lateinit var platformViewFactory: CameraPlatformViewFactory

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        applicationContext = binding.applicationContext

        platformViewFactory = CameraPlatformViewFactory(binding.binaryMessenger)
        binding.platformViewRegistry.registerViewFactory(
            "CameraPlatformView",
            platformViewFactory
        )

        methodChannel = MethodChannel(
            binding.binaryMessenger,
            "photo_capture_channel_default"
        )
        methodChannel.setMethodCallHandler(this)

        Log.d(TAG, "Attached to engine")
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        PredictorManager.releaseModel()
        platformViewFactory.dispose()
        Log.d(TAG, "Detached from engine")
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        detachFromActivityInternal()

        activity = binding.activity
        activityBinding = binding

        platformViewFactory.setActivity(activity)
        binding.addRequestPermissionsResultListener(this)

        Log.d(TAG, "Attached to activity: ${activity?.javaClass?.simpleName}")
    }

    private fun detachFromActivityInternal() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        activityBinding = null
        activity = null
        platformViewFactory.setActivity(null)
    }

    override fun onDetachedFromActivity() {
        detachFromActivityInternal()
        Log.d(TAG, "Detached from activity")
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachFromActivityInternal()
        Log.d(TAG, "Detached from activity")
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.d(TAG, "Reattached to activity")
        onAttachedToActivity(binding)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "ref_frame_predict" -> {
                try {
                    Log.d(TAG, "ref_frame_predict start!")

                    val bytes = call.argument<ByteArray>("bytes") ?: return
                    val assetPredictions = call.argument<String?>("assetPredictions")
                    val platformView = platformViewFactory.activeViews.values.first()
                    if (platformView != null) {

                        if (assetPredictions != null) {
                            val json = Json {
                                ignoreUnknownKeys = true
                            }
                            platformView.cameraViewInstance.refDetectionResult = json.decodeFromString<InstanceObj>(assetPredictions)
                            return
                        }

                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        // saveBitmapToFile(applicationContext, bitmap, "ref_frame_predict_${System.currentTimeMillis()}")

                        val predictorInstance = platformView.cameraViewInstance.predictorInstance

                        if (predictorInstance != null) {
                            Log.d(TAG, "ref_frame_predict predictorInstance ok!")

                            val w = bitmap.width
                            val h = bitmap.height
                            Log.d(TAG, "predictorInstance result ${predictorInstance}")
                            platformView.cameraViewInstance.refDetectionResult =
                                predictorInstance.predict(
                                    bitmap,
                                    w,
                                    h,
                                    rotateForCamera = false,
                                    isLandscape = false
                                )
                            Log.d(
                                TAG,
                                "ref_frame_predict result ${platformView.cameraViewInstance.refDetectionResult}"
                            )
                        }

                    } else {
                        result.error(
                            "VIEW_NOT_FOUND",
                            "CameraPlatformView with id 0 not found",
                            null
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ref_frame_predict Error setting model", e)
                    result.error(
                        "ref_frame_predict",
                        "Error predicting for ref frame: ${e.message}",
                        null
                    )
                }
            }

            "update_similarity_score" -> {
                try {
                    val similarityValue = call.argument<Double>("value")
                    if (similarityValue != null) {
                        val platformView = platformViewFactory.activeViews.values.first()
                        if (platformView != null) {
                            platformView.cameraViewInstance.comparePoseThreshold = similarityValue / 100.0
                            println("Similarity score from flutter slider sent: $similarityValue%")
                            result.success(null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "send_score_slider_button Error sending similarity score from flutter slider", e)
                    result.error(
                        "send_score_slider_button",
                        "Error sending similarity score: ${e.message}",
                        null
                    )
                }
            }

            "update_visualization_settings" -> {
                try {
                    val visualizationMode = call.argument<String>("value")
                    if (visualizationMode != null) {
                        val platformView = platformViewFactory.activeViews.values.first()
                        if (platformView != null) {
                            platformView.cameraViewInstance.visualizationMode = visualizationMode.lowercase()
                            println("Visualization mode from flutter sent: $visualizationMode%")
                            result.success(null)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "update_visualization_settings Error sending visualization mode from flutter slider", e)
                    result.error(
                        "update_visualization_settings",
                        "Error sending visualization mode: ${e.message}",
                        null
                    )
                }
            }

            else -> result.notImplemented()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {

        val activeViews = ArrayList(platformViewFactory.activeViews.values)
        var handled = false

        for (platformView in activeViews) {
            try {
                platformView.cameraViewInstance.onRequestPermissionsResult(
                    requestCode,
                    permissions,
                    grantResults
                )
                handled = true

            } catch (e: Exception) {
                Log.e(TAG, "Error processing permission result for PlatformView", e)
            }
        }

        if (!handled) {
            Log.d(TAG, "onRequestPermissionsResult: no PlatformView handled the result")
        } else {
            Log.d(TAG, "onRequestPermissionsResult: handled by a PlatformView")
        }

        return handled
    }
}