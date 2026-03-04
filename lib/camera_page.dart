import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:preciosai/logger.dart';
import 'package:preciosai/recognition_view.dart';
import 'package:preciosai/small_reference_photo.dart';

class CameraPage extends StatefulWidget {
  final String refImagePath;

  const CameraPage({super.key, required this.refImagePath});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  final methodChannel = const MethodChannel('photo_capture_channel_default');

  bool _isFrontCamera = false;

  // Settings
  bool showSettings = false;
  double sliderValue = 3;
  bool flashOn = false;
  bool nightModeOn = false;
  int similarityDegreeOption = 0; // 0, 1 или 2
  Map<String, dynamic>? refPredictionsJsonMap;

  Future<void> init() async {
    // TODO wait for the model to initialize normally
    await Future.delayed(const Duration(milliseconds: 2000));
    if (!mounted) return;
    await readJsonFromAssets();
    pickAndSendRefImage(widget.refImagePath);
  }

  @override
  void initState() {
    super.initState();
    init();
  }

  Future<void> readJsonFromAssets() async {
    final String jsonString = await rootBundle.loadString('assets/gallery_images/ref_predictions.json');
    refPredictionsJsonMap = jsonDecode(jsonString);
  }

  Future<void> sendImageToNative(Uint8List bytes, String? assetPredictions) async {
    try {
      await methodChannel.invokeMethod('ref_frame_predict', {'bytes': bytes, 'assetPredictions': assetPredictions});
    } on PlatformException catch (e) {
      Logger.error('Error in reference image sending: ${e.message}');
    }
  }

  Future<void> pickAndSendRefImage(String? imagePath) async {
    Uint8List? bytes;
    String? assetPredictions;
    if (imagePath == null) {
      final picker = ImagePicker();
      final picked = await picker.pickImage(source: ImageSource.gallery);
      if (picked != null) {
        bytes = await File(picked.path).readAsBytes();
      }
    } else {
      if (imagePath.startsWith('assets/')) {
        final data = await rootBundle.load(imagePath);
        bytes = data.buffer.asUint8List();

        String keyName = imagePath.split('/').last;
        if (refPredictionsJsonMap != null && refPredictionsJsonMap!.containsKey(keyName)) {
          assetPredictions = jsonEncode(refPredictionsJsonMap![keyName]);
        }
      } else {
        bytes = await File(imagePath).readAsBytes();
      }
    }
    if (bytes != null) await sendImageToNative(bytes, assetPredictions);
  }

  @override
  Widget build(BuildContext context) {
    final orientation = MediaQuery.of(context).orientation;
    final isLandscape = orientation == Orientation.landscape;
    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;

    return Scaffold(
      body: Stack(
        children: [
          RecognitionView(
            key: const ValueKey('detection_view_main'),
            refImagePath: widget.refImagePath,
          ),

          // Small reference frame in the left bottom corner
          ExpandableCornerImage(imagePath: widget.refImagePath),

          // Settings
          Positioned(
            left: isLandscape ? 32 : 16,
            bottom:
                MediaQuery.of(context).padding.top + (isLandscape ? 160 : 80),
            child: CircleAvatar(
              radius: isLandscape ? 20 : 24,
              backgroundColor: Colors.black.withValues(alpha: 0.5),
              child: IconButton(
                icon: const Icon(Icons.settings, color: Colors.white),
                onPressed: () {
                  setState(() {
                    showSettings = !showSettings;
                  });
                },
              ),
            ),
          ),

          // TODO fix camera switch
          // Camera switch
          Positioned(
            bottom:
                MediaQuery.of(context).padding.top + (isLandscape ? 32 : 16),
            left: isLandscape ? 32 : 16,
            child: CircleAvatar(
              radius: isLandscape ? 20 : 24,
              backgroundColor: Colors.black.withValues(alpha: 0.5),
              child: IconButton(
                icon: const Icon(Icons.flip_camera_ios, color: Colors.white),
                onPressed: () {
                  setState(() {
                    _isFrontCamera = !_isFrontCamera;
                    // Reset zoom level when switching to front camera
                  });
                  //controller.switchCamera();
                },
              ),
            ),
          ),

          // Overlay для закрытия по тапу вне окна
          if (showSettings)
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  setState(() => showSettings = false);
                },
                child: Container(
                  color: Colors.transparent,
                ),
              ),
            ),

          // Settings animation
          AnimatedPositioned(
            duration: const Duration(milliseconds: 350),
            curve: Curves.easeOutCubic,
            left: 0,
            right: 0,
            bottom: showSettings ? 0 : -screenHeight * 0.35,
            height: screenHeight * 0.35,
            child: GestureDetector(
              onVerticalDragUpdate: (details) {
                if (details.delta.dy > 10) {
                  setState(() => showSettings = false);
                }
              },
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.black.withOpacity(0.55),
                  borderRadius: const BorderRadius.vertical(
                    top: Radius.circular(24),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    const Text(
                      'Degree of similarity',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white, fontSize: 16),
                    ),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: List.generate(3, (index) {
                        final labels = ['Low', 'Medium', 'High'];
                        return GestureDetector(
                          onTap: () {
                            setState(() {
                              similarityDegreeOption = index;
                            });
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 8,
                              vertical: 4,
                            ),
                            child: Row(
                              children: [
                                Radio<int>(
                                  value: index,
                                  groupValue: similarityDegreeOption,
                                  onChanged: (value) {
                                    setState(() {
                                      similarityDegreeOption = value!;
                                    });
                                  },
                                  activeColor: Colors.white,
                                  //fillColor: MaterialStateColor.resolveWith((states) => Colors.indigo),
                                ),
                                Text(
                                  labels[index],
                                  style: const TextStyle(color: Colors.white),
                                ),
                              ],
                            ),
                          ),
                        );
                      }),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'Number of resulting frames',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white, fontSize: 16),
                    ),
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        valueIndicatorTextStyle: const TextStyle(
                          color: Colors.black,
                          fontWeight: FontWeight.bold,
                        ),
                        valueIndicatorColor: Colors.white,
                        showValueIndicator: ShowValueIndicator.onDrag,
                      ),
                      child: Slider(
                        min: 1,
                        max: 5,
                        divisions: 4,
                        value: sliderValue,
                        activeColor: Colors.white,
                        inactiveColor: Colors.white38,
                        label: sliderValue.toStringAsFixed(0),
                        onChanged: (value) =>
                            setState(() => sliderValue = value),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        SizedBox(width: screenWidth / 3 - 40),
                        GestureDetector(
                          onTap: () {
                            setState(() => flashOn = !flashOn);
                            // TODO add flash on function here
                          },
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 250),
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: flashOn ? Colors.yellow : Colors.grey,
                              border: Border.all(color: Colors.white, width: 2),
                            ),
                            child: flashOn
                                ? const Icon(
                                    Icons.flash_on,
                                    color: Colors.black,
                                    size: 30,
                                  )
                                : const Icon(
                                    Icons.flash_off,
                                    color: Colors.black,
                                    size: 30,
                                  ),
                          ),
                        ),

                        SizedBox(width: screenWidth / 3 - 40),
                        GestureDetector(
                          onTap: () {
                            setState(() => nightModeOn = !nightModeOn);
                            // TODO add night mode on function here
                          },
                          child: AnimatedContainer(
                            duration: const Duration(milliseconds: 250),
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: nightModeOn ? Colors.yellow : Colors.grey,
                              border: Border.all(color: Colors.white, width: 2),
                            ),
                            child: nightModeOn
                                ? const Icon(
                                    Icons.nightlight,
                                    color: Colors.black,
                                    size: 30,
                                  )
                                : const Icon(
                                    Icons.light_mode,
                                    color: Colors.black,
                                    size: 30,
                                  ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
