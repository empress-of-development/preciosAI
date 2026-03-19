import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:preciosai/camera_page_settings.dart';
import 'package:preciosai/logger.dart';
import 'package:preciosai/recognition_view.dart';
import 'package:preciosai/small_reference_photo.dart';
import 'package:showcaseview/showcaseview.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:lottie/lottie.dart';

class CameraPage extends StatefulWidget {
  final String refImagePath;

  const CameraPage({super.key, required this.refImagePath});

  @override
  State<CameraPage> createState() => _CameraPageState();
}

class _CameraPageState extends State<CameraPage> {
  final methodChannel = const MethodChannel('photo_capture_channel_default');
  final methodChannelAdd = const MethodChannel('photo_capture_channel_add');

  // TODO костыль!!!
  String modelType = 'mediapipe';
  bool _isFrontCamera = false;

  // Settings
  bool showSettings = false;
  double sliderValue = 3;
  bool flashOn = false;
  bool nightModeOn = false;
  int similarityDegreeOption = 0; // 0, 1 или 2
  Map<String, dynamic>? refPredictionsJsonMap;

  // Индикатор загруженности модели
  static bool modelLoaded = false;
  final Completer<void> _modelReadyCompleter = Completer<void>();

  // Showcase Keys
  final GlobalKey _stepOne = GlobalKey();
  final GlobalKey _stepTwo = GlobalKey();
  final GlobalKey _stepThree = GlobalKey();
  final GlobalKey _stepFour = GlobalKey();
  final GlobalKey _stepFive = GlobalKey();

  // Текстовые подсказки делаются только для первого кадра в рамках запуска приложения
  static bool _isFirstVisit = true;
  bool showStatusMessage = false;
  String statusText = '';
  bool _onboardingChecked = false;

  Future<void> init() async {
    if (!mounted) return;
    await readJsonFromAssets();
    pickAndSendRefImage(widget.refImagePath);
  }

  void setupChannelListener() {
    methodChannelAdd.setMethodCallHandler((MethodCall call) async {
      switch (call.method) {
        case 'onModelReady':
          final args = call.arguments as Map<Object?, Object?>;
          Logger.info("Kotlin model loading status: ${args['status']}");
          setState(() {
            modelLoaded = true;
          });

          if (!_modelReadyCompleter.isCompleted) {
            _modelReadyCompleter.complete();
          }
          break;
      }
    });
  }

  @override
  void initState() {
    super.initState();
    setupChannelListener();
    init();
  }

  @override
  void dispose() {
    methodChannel.setMethodCallHandler(null);
    methodChannelAdd.setMethodCallHandler(null);
    super.dispose();
  }

  Future<void> readJsonFromAssets() async {
    final String jsonString = await rootBundle.loadString(
      'assets/gallery_images/ref_predictions_$modelType.json',
    );
    refPredictionsJsonMap = jsonDecode(jsonString);
  }

  Future<void> _startOnboardingFlow() async {
    final prefs = await SharedPreferences.getInstance();
    final isFirstRun = prefs.getBool('isFirstRun_cameraPage') ?? true;

    if (isFirstRun && mounted) {
      await prefs.setBool('isFirstRun_cameraPage', false);

      ShowcaseView.get().startShowCase([
        _stepOne,
        _stepTwo,
        _stepThree,
        _stepFour,
        _stepFive,
      ]);
    } else {
      if (_isFirstVisit && mounted) {
        showHintsSequence();
        _isFirstVisit = false;
      }
    }
  }

  Future<void> sendImageToNative(
      Uint8List bytes,
      String? assetPredictions,
      ) async {

    if (!modelLoaded) {
      await _modelReadyCompleter.future;
    }

    if (!_onboardingChecked) {
      _onboardingChecked = true;
      _startOnboardingFlow();
    }

    try {
      await methodChannel.invokeMethod('ref_frame_predict', {
        'bytes': bytes,
        'assetPredictions': assetPredictions,
      });
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
        if (refPredictionsJsonMap != null &&
            refPredictionsJsonMap!.containsKey(keyName)) {
          assetPredictions = jsonEncode(refPredictionsJsonMap![keyName]);
        }
      } else {
        bytes = await File(imagePath).readAsBytes();
      }
    }
    if (bytes != null) await sendImageToNative(bytes, assetPredictions);
  }

  Future<void> showHintsSequence() async {
    final List<String> messages = [
      //'Now you see schematic skeleton of the reference photo',
      //'And a second skeleton that matches your model',
      "If you don't see skeleton that matches your model, move the camera a little",
      'Place the model in the highlighted sector and merge the skeletons',
      //'Then you need to merge the skeletons using auxiliary color sectors.',
      //'For the perfect photo each of them must be green',
      'Move the camera slowly and give your model cues',
      //'The zoom will be adjusted automatically, but you can adjust it yourself if necessary',
      //'Currently, only one person can be photographed'
    ];

    for (String msg in messages) {
      if (!mounted) return;

      setState(() {
        statusText = msg;
        showStatusMessage = true;
      });

      await Future.delayed(const Duration(seconds: 4));
    }

    if (mounted) {
      setState(() => showStatusMessage = false);
    }
  }

  Future<void> _switchCamera() async {
    setState(() {
      _isFrontCamera = !_isFrontCamera;
    });

    try {
      await methodChannel.invokeMethod('switchCamera', {
        'isFront': _isFrontCamera,
      });
    } on PlatformException catch (e) {
      Logger.info('Error in camera switching: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    final orientation = MediaQuery.of(context).orientation;
    final isLandscape = orientation == Orientation.landscape;
    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        // fit: StackFit.expand,
        children: [
          RecognitionView(
            key: const ValueKey('detection_view_main'),
            refImagePath: widget.refImagePath,
          ),

          // Small reference frame in the left bottom corner
          ExpandableCornerImage(imagePath: widget.refImagePath),

          // Settings
          /*
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
           */

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
                  _switchCamera();
                },
              ),
            ),
          ),

          Showcase.withWidget(
            key: _stepOne,
            overlayOpacity: 0.85,
            targetShapeBorder: const CircleBorder(),
            targetPadding: EdgeInsets.zero,
            container: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () {
                ShowcaseView.get().completed(_stepOne);
              },
              child: Align(
                alignment: Alignment.center,
                child: SizedBox(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(height: MediaQuery.of(context).size.height * 0.1),

                      const Text(
                        'You will see schematic skeleton of the reference photo '
                            'and a skeleton that matches your model.\n'
                            ' Place the model in the highlighted sector and merge the skeletons.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Lottie.asset(
                        'assets/onboarding/animation/pose_matching.json',
                        height: MediaQuery.of(context).size.height * 0.5,
                        fit: BoxFit.contain,
                      ),
                    ],
                  ),
                ),
              ),
            ),

            child: const SizedBox(width: 10, height: 10),
          ),

          Showcase.withWidget(
              key: _stepTwo,
              overlayOpacity: 0.85,
              targetShapeBorder: const CircleBorder(),
              targetPadding: EdgeInsets.zero,
              container: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  ShowcaseView.get().completed(_stepTwo);
                },
                child: Align(
                  alignment: Alignment.center,
                  child: SizedBox(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        SizedBox(height: MediaQuery.of(context).size.height * 0.1),

                        const Text(
                          'Auxiliary sectors display the correspondence of body parts to the desired pose. '
                              'For a perfect shot, each of them should be green.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 24),
                        Lottie.asset(
                          'assets/onboarding/animation/pose_zones.json',
                          height: MediaQuery.of(context).size.height * 0.5,
                          fit: BoxFit.contain,
                        ),
                        const SizedBox(height: 24),
                        const Text(
                          'The zoom will be adjusted automatically, but you can change it yourself if necessary. '
                              'Currently, only one person can be photographed.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),

            child: const SizedBox(width: 10, height: 10),
          ),

          Showcase.withWidget(
            key: _stepThree,
            overlayOpacity: 0.85,
            targetShapeBorder: const CircleBorder(),
            targetPadding: EdgeInsets.zero,
            container: Align(
              alignment: Alignment.center,
              child: SizedBox(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    SizedBox(height: MediaQuery.of(context).size.height * 0.3),
                    const Text(
                      'The resulting photo will be saved automatically in your gallery in the PreciosAI application folder.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 24,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ]
                )
              ),
            ),
            child: const SizedBox(width: 10, height: 10),
          ),

          // Overlay для закрытия по тапу вне окна
          if (showSettings)
            Positioned.fill(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () {
                  setState(() => showSettings = false);
                },
                child: Container(color: Colors.transparent),
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

          // Кнопка визуализации
          VisualizationSettingsButton(
            showcaseKey: _stepFour,
          ),

          // Кнопка слайдера для регулирования желаемой степени похожести позы
          PoseSimilaritySliderButton(
            showcaseKey: _stepFive,
            onShowcaseTap: () {
              if (_isFirstVisit && mounted) {
                showHintsSequence();
                _isFirstVisit = false;
              }
            },
          ),

          if (!modelLoaded)
            Positioned(
              top: screenHeight * 0.425,
              left: 0,
              right: 0,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const CircularProgressIndicator(
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.indigo),
                    strokeWidth: 3,
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    "Model loading\nWait a second, please",
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                      shadows: [
                        Shadow(blurRadius: 10, color: Colors.black54),
                      ],
                    ),
                  ),
                ],
              ),
            ),

          if (showStatusMessage)
            Positioned(
              top: screenHeight * 0.2,
              left: 0,
              right: 0,
              child: Center(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 400),
                  transitionBuilder: (Widget child, Animation<double> animation) {
                    return FadeTransition(
                      opacity: animation,
                      child: ScaleTransition(scale: animation, child: child),
                    );
                  },
                  child: Container(
                    key: ValueKey<String>(statusText),
                    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                    decoration: BoxDecoration(
                      color: Colors.black.withOpacity(0.7),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      statusText,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white, fontSize: 16),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
