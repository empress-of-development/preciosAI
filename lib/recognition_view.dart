import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart' show defaultTargetPlatform;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:preciosai/logger.dart';
import 'package:preciosai/result_page.dart';
import 'package:saver_gallery/saver_gallery.dart';

class RecognitionView extends StatefulWidget {
  final String? refImagePath;

  const RecognitionView({super.key, this.refImagePath});

  @override
  State<RecognitionView> createState() => RecognitionViewState();
}

class RecognitionViewState extends State<RecognitionView> {
  late MethodChannel _methodChannel;

  final String _viewId = UniqueKey().toString();
  int? _platformViewId;

  @override
  void initState() {
    super.initState();

    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);

    final controlChannelName =
        'com.preciosai.photo_capture_plugin/controlChannel_$_viewId';
    _methodChannel = MethodChannel(controlChannelName);
  }

  @override
  void dispose() {
    Logger.debug('RecognitionView.dispose() called - starting cleanup');

    // Clean up method channel handler
    try {
      _methodChannel.setMethodCallHandler(null);
      Logger.debug('RecognitionView: Method channel handler cleared');
    } catch (e) {
      Logger.error(
        'RecognitionView: Error clearing method channel handler: $e',
      );
    }

    // TODO check disposing models
    // Dispose model instance using viewId as instanceId
    // This prevents memory leaks by ensuring the model is released
    // if (_platformViewId != null) {

    Logger.debug(
      'RecognitionView.dispose() completed - calling super.dispose()',
    );
    super.dispose();
  }

  void _continue(bytes) {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) =>
            ResultPage(bytes: bytes, refImagePath: widget.refImagePath!),
      ),
    );
  }

  Future<dynamic> handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'goToResult':
        final List<dynamic> serialized = call.arguments['bytes'];

        final List<Uint8List> bytes = serialized
            .map((e) => e['data'] as Uint8List)
            .toList();

        _continue(bytes);

      case 'requestPhoto':
        final picked = await ImagePicker().pickImage(
          source: ImageSource.camera,
        );
        if (picked == null) return;

        final file = File(picked.path);
        final bytes = await file.readAsBytes();

        final status = await Permission.photos.request();
        if (!status.isGranted) {
          Logger.error('RecognitionView: requestPhoto - Permission denied');
          return;
        }

        final result = await SaverGallery.saveImage(
          bytes,
          fileName: 'photo_${DateTime.now().millisecondsSinceEpoch}.jpg',
          skipIfExists: false,
          //androidExistNotSave: false,
        );

        Logger.debug(
          'RecognitionView: requestPhoto - Gallery save result: $result',
        );
      default:
        Logger.error('RecognitionView: Unknown method call: ${call.method}');
        return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final creationParams = <String, dynamic>{
      'numItemsThreshold': 10,
      'viewId': _viewId,
    };

    Widget platformView;
    if (defaultTargetPlatform == TargetPlatform.android) {
      platformView = AndroidView(
        viewType: 'CameraPlatformView',
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    } else {
      platformView = const Center(
        child: Text('Platform is not supported for RecognitionView'),
      );
    }
    return platformView;
  }

  void _onPlatformViewCreated(int id) {
    Logger.debug(
      'RecognitionView: Platform view created with system id: $id, our viewId: $_viewId',
    );
    _platformViewId = id;
    _methodChannel.setMethodCallHandler(handleMethodCall);
  }
}
