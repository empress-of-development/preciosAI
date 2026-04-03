import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:preciosai/l10n/app_localizations.dart';
import 'package:preciosai/logger.dart';
import 'package:showcaseview/showcaseview.dart';

class PoseSimilaritySliderButton extends StatefulWidget {
  final GlobalKey? showcaseKey;
  final VoidCallback? onShowcaseTap;

  const PoseSimilaritySliderButton({
    Key? key,
    this.showcaseKey,
    this.onShowcaseTap,
  }) : super(key: key);

  @override
  State<PoseSimilaritySliderButton> createState() =>
      _PoseSimilaritySliderButtonState();
}

class _PoseSimilaritySliderButtonState
    extends State<PoseSimilaritySliderButton> {
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

  Widget _buildShowcaseWrapper({
    required String description,
    required Widget child,
  }) {
    if (widget.showcaseKey == null) return child;
    return Showcase(
      key: widget.showcaseKey!,
      description: description,
      disposeOnTap: true,
      onTargetClick: widget.onShowcaseTap ?? () {},
      onToolTipClick: widget.onShowcaseTap ?? () {},
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Stack(
      children: [
        if (_isSliderVisible)
          Positioned.fill(
            child: GestureDetector(
              onTap: () => setState(() => _isSliderVisible = false),
              behavior: HitTestBehavior.translucent,
              child: Container(color: Colors.transparent),
            ),
          ),

        Positioned(
          top: 70,
          right: 16,
          child: _buildShowcaseWrapper(
            description: l10n.degreeOfSimilarityDesc,
            child: GestureDetector(
              onTap: () {
                setState(() {
                  _isSliderVisible = !_isSliderVisible;
                });
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
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
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.tune, color: Colors.white, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      l10n.degreeOfSimilarity,
                      style: const TextStyle(
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
        ),
        // Ползунок
        Positioned(
          top: 125,
          right: 16,
          child: AnimatedOpacity(
            opacity: _isSliderVisible ? 1.0 : 0.0,
            duration: const Duration(milliseconds: 250),
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
  final GlobalKey? showcaseKey;
  final VoidCallback? onShowcaseTap;

  const VisualizationSettingsButton({
    Key? key,
    this.showcaseKey,
    this.onShowcaseTap,
  }) : super(key: key);

  @override
  State<VisualizationSettingsButton> createState() =>
      _VisualizationSettingsButtonState();
}

class _VisualizationSettingsButtonState
    extends State<VisualizationSettingsButton> {
  bool _isPanelVisible = false;
  String _selectedOption = 'skeletonCapsules';
  final List<String> _options = [
    'empty',
    'skeleton',
    'capsules',
    'skeletonCapsules',
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

  Widget _buildShowcaseWrapper({
    required String description,
    required Widget child,
  }) {
    if (widget.showcaseKey == null) return child;
    return Showcase(
      key: widget.showcaseKey!,
      description: description,
      disposeOnTap: true,
      onTargetClick: widget.onShowcaseTap ?? () {},
      onToolTipClick: widget.onShowcaseTap ?? () {},
      child: child,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Stack(
      children: [
        if (_isPanelVisible)
          Positioned.fill(
            child: GestureDetector(
              onTap: () => setState(() => _isPanelVisible = false),
              behavior: HitTestBehavior.translucent,
              child: Container(color: Colors.transparent),
            ),
          ),

        Positioned(
          top: 70,
          left: 16,
          child: _buildShowcaseWrapper(
            description: l10n.visualizationDesc,
            child: GestureDetector(
              onTap: () {
                setState(() {
                  _isPanelVisible = !_isPanelVisible;
                });
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
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
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.brush, color: Colors.white, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      l10n.visualization,
                      style: const TextStyle(
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
        ),
        // Выпадающая панель с выбором опций
        Positioned(
          top: 125,
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
                  children: _options
                      .map((option) => _buildOptionItem(option))
                      .toList(),
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
    final l10n = AppLocalizations.of(context)!;

    return InkWell(
      onTap: () {
        setState(() {
          _selectedOption = title;
          _isPanelVisible = false;
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
              l10n.visualizationType(title),
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
