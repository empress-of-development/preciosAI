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


class VisualizationSettingsButton extends StatefulWidget {
  const VisualizationSettingsButton({Key? key}) : super(key: key);

  @override
  State<VisualizationSettingsButton> createState() => _VisualizationSettingsButtonState();
}

class _VisualizationSettingsButtonState extends State<VisualizationSettingsButton> {
  bool _isPanelVisible = false;

  String _selectedOption = "Skeleton+Capsules";

  final List<String> _options = [
    "Empty",
    "Skeleton",
    "Capsules",
    "Skeleton+Capsules"
  ];

  static const methodChannel = MethodChannel('photo_capture_channel_default');

  Future<void> _sendVisualizationSettingsToKotlin(String value) async {
    try {
      await methodChannel.invokeMethod('update_visualization_settings', {
        'value': value,
      });
    } on PlatformException catch (e) {
      Logger.error(
        "Error sending visualization settings from flutter slider to Kotlin: '${e.message}'.",
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        // Кнопка
        Positioned(
          top: 45,
          left: 16,
          child: GestureDetector(
            onTap: () {
              setState(() {
                _isPanelVisible = !_isPanelVisible;
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
                    blurRadius: 6,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.brush, color: Colors.white, size: 18),
                  SizedBox(width: 8),
                  Text(
                    "Visualization",
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

        // Выпадающая панель с выбором опций
        Positioned(
          top: 100,
          left: 16,
          child: AnimatedOpacity(
            opacity: _isPanelVisible ? 1.0 : 0.0,
            duration: const Duration(milliseconds: 250),
            child: IgnorePointer(
              ignoring: !_isPanelVisible,
              child: Container(
                width: 220,
                padding: const EdgeInsets.symmetric(vertical: 8),
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
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: _options.map((option) => _buildOptionItem(option)).toList(),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildOptionItem(String title) {
    final isSelected = _selectedOption == title;

    return InkWell(
      onTap: () {
        setState(() {
          _selectedOption = title;
          _isPanelVisible = false; // Автоматически скрываем панель после выбора
        });

        _sendVisualizationSettingsToKotlin(title);
      },
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        color: isSelected ? Colors.white.withOpacity(0.15) : Colors.transparent,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              title,
              style: TextStyle(
                color: isSelected ? Colors.white : Colors.white70,
                fontSize: 14,
                fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
              ),
            ),
            if (isSelected)
              const Icon(Icons.check, color: Colors.white, size: 18),
          ],
        ),
      ),
    );
  }
}
