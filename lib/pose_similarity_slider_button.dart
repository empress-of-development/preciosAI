import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:preciosai/logger.dart';

class PoseSimilaritySliderButton extends StatefulWidget {
  const PoseSimilaritySliderButton({Key? key}) : super(key: key);

  @override
  State<PoseSimilaritySliderButton> createState() => _PoseSimilaritySliderButtonState();
}

class _PoseSimilaritySliderButtonState extends State<PoseSimilaritySliderButton> {
  bool _isSliderVisible = false;
  double _sliderValue = 70.0;
  static const methodChannel = MethodChannel('photo_capture_channel_default');

  Future<void> _sendSimilarityToKotlin(double value) async {
    try {
      await methodChannel.invokeMethod('update_similarity_score', {
        'value': value,
      });
    } on PlatformException catch (e) {
      Logger.error(
        "Error sending similarity score from flutter slider to Kotlin: '${e.message}'.",
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // Кнопка в правом верхнем углу
        Positioned(
          top: 45,
          right: 16,
          child: GestureDetector(
            onTap: () {
              setState(() {
                _isSliderVisible = !_isSliderVisible;
              });
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.indigo.withOpacity(0.7),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.2),
                    // Легкая тень для объема
                    blurRadius: 6,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.tune, color: Colors.white, size: 18),
                  SizedBox(width: 8),
                  Text(
                    "Degree of similarity",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),

        // Ползунок
        Positioned(
          top: 100,
          right: 16,
          child: AnimatedOpacity(
            opacity: _isSliderVisible ? 1.0 : 0.0,
            duration: const Duration(milliseconds: 250),
            // Игнорируем нажатия, когда ползунок скрыт
            child: IgnorePointer(
              ignoring: !_isSliderVisible,
              child: Container(
                width: 260,
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: Colors.indigo.shade900.withOpacity(0.8),
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.3),
                      blurRadius: 8,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Row(
                  children: [
                    Text(
                      '${_sliderValue.toInt()}%',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: SliderTheme(
                        data: SliderTheme.of(context).copyWith(
                          trackHeight: 4,
                          thumbShape: const RoundSliderThumbShape(
                            enabledThumbRadius: 8,
                          ),
                          valueIndicatorTextStyle: const TextStyle(
                            color: Colors.indigo,
                            fontWeight: FontWeight.bold,
                          ),
                          valueIndicatorColor: Colors.white,
                          showValueIndicator: ShowValueIndicator.onDrag,
                        ),
                        child: Slider(
                          min: 50,
                          max: 90,
                          divisions: 8,
                          value: _sliderValue,
                          activeColor: Colors.white,
                          inactiveColor: Colors.white38,
                          label: '${_sliderValue.toInt()}%',
                          onChanged: (value) {
                            setState(() {
                              _sliderValue = value;
                            });
                          },
                          // onChangeEnd срабатывает, когда палец отпущен
                          // и только тогда скор обновляется
                          onChangeEnd: (value) {
                            _sendSimilarityToKotlin(value);
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
