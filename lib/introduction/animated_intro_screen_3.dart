import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:preciosai/l10n/app_localizations.dart';

class AnimatedIntroScreen3 extends StatefulWidget {
  const AnimatedIntroScreen3({
    super.key,
    required this.centerAsset,
    required this.isActive,
  });

  final String centerAsset;
  final bool isActive;

  @override
  State<AnimatedIntroScreen3> createState() => _AnimatedIntroScreen3State();
}

class _AnimatedIntroScreen3State extends State<AnimatedIntroScreen3>
    with TickerProviderStateMixin {
  late final AnimationController _staggerC;
  late final AnimationController _borderC;

  late final Animation<double> _titleOpacity;
  late final Animation<Offset> _titleSlide;

  late final Animation<double> _imageOpacity;
  late final Animation<double> _imageScale;

  late final Animation<double> _descOpacity;
  late final Animation<Offset> _descSlide;

  double? _assetAspectRatio; // width / height

  @override
  void initState() {
    super.initState();

    _staggerC = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );

    // непрерывное движение блика
    _borderC = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );

    _titleOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.0, 0.3, curve: Curves.easeOut),
    );

    _titleSlide = Tween(begin: const Offset(0, -0.20), end: Offset.zero)
        .animate(
          CurvedAnimation(
            parent: _staggerC,
            curve: const Interval(0.0, 0.35, curve: Curves.easeOutCubic),
          ),
        );

    _imageOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.25, 0.7, curve: Curves.easeOut),
    );

    _imageScale = Tween(begin: 0.94, end: 1.0).animate(
      CurvedAnimation(
        parent: _staggerC,
        curve: const Interval(0.25, 0.7, curve: Curves.easeOutBack),
      ),
    );

    _descOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.6, 1.0, curve: Curves.easeOut),
    );

    _descSlide = Tween(begin: const Offset(0, 0.18), end: Offset.zero).animate(
      CurvedAnimation(
        parent: _staggerC,
        curve: const Interval(0.6, 1.0, curve: Curves.easeOutCubic),
      ),
    );

    _resolveAssetAspectRatio(widget.centerAsset);

    if (widget.isActive) {
      _start();
    }
  }

  void _start() {
    _staggerC.forward(from: 0);
    _borderC.repeat();
  }

  @override
  void didUpdateWidget(covariant AnimatedIntroScreen3 oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (oldWidget.centerAsset != widget.centerAsset) {
      _assetAspectRatio = null;
      _resolveAssetAspectRatio(widget.centerAsset);
    }

    if (!oldWidget.isActive && widget.isActive) {
      _start();
    }

    if (oldWidget.isActive && !widget.isActive) {
      _staggerC.reset();
      _borderC.stop();
    }
  }

  void _resolveAssetAspectRatio(String asset) {
    final image = AssetImage(asset);
    final stream = image.resolve(const ImageConfiguration());
    late final ImageStreamListener listener;

    listener = ImageStreamListener(
      (info, _) {
        final w = info.image.width.toDouble();
        final h = info.image.height.toDouble();
        if (mounted) setState(() => _assetAspectRatio = w / h);
        stream.removeListener(listener);
      },
      onError: (error, stack) {
        debugPrint('❌ Failed to resolve asset size: $error');
        stream.removeListener(listener);
      },
    );

    stream.addListener(listener);
  }

  @override
  void dispose() {
    _staggerC.dispose();
    _borderC.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ar = _assetAspectRatio ?? (9 / 19.5);
    final l10n = AppLocalizations.of(context)!;

    return LayoutBuilder(
      builder: (context, constraints) {
        final w = constraints.maxWidth;
        final h = constraints.maxHeight;

        // Размер изображения по ширине
        final desiredWidth = w;
        double desiredHeight = desiredWidth / ar;

        final maxImageHeight = h * 0.55;
        if (desiredHeight > maxImageHeight) {
          desiredHeight = maxImageHeight;
        }

        final finalWidth = desiredHeight * ar;
        final finalHeight = desiredHeight;

        final imageTop = h * 0.2;
        final descTopSpacing = 28.0;

        final titleTop = imageTop - 110; // высота блока заголовка
        final descTop = imageTop + finalHeight + descTopSpacing;

        return Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFF2C0F63), Color(0xFF1B0A3D), Color(0xFF12072D)],
            ),
          ),
          child: SafeArea(
            child: Stack(
              children: [
                Positioned(
                  left: 24,
                  right: 24,
                  top: titleTop < 40 ? 40 : titleTop,
                  child: SlideTransition(
                    position: _titleSlide,
                    child: FadeTransition(
                      opacity: _titleOpacity,
                      child: Text(
                        l10n.letCameraGuideYou,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 34,
                          fontWeight: FontWeight.w800,
                          height: 1.05,
                        ),
                      ),
                    ),
                  ),
                ),

                Positioned(
                  left: (w - finalWidth) / 2,
                  top: imageTop,
                  width: finalWidth,
                  height: finalHeight,
                  child: FadeTransition(
                    opacity: _imageOpacity,
                    child: ScaleTransition(
                      scale: _imageScale,
                      child: ShimmerBorderBox(
                        borderT: _borderC,
                        width: finalWidth,
                        height: finalHeight,
                        radius: 28,
                        borderWidth: 4,
                        gap: 10,
                        child: Container(
                          color: const Color(0xFF0E0B1C),
                          alignment: Alignment.center,
                          child: Image.asset(
                            widget.centerAsset,
                            fit: BoxFit.contain,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),

                Positioned(
                  left: 24,
                  right: 24,
                  top: descTop,
                  child: SlideTransition(
                    position: _descSlide,
                    child: FadeTransition(
                      opacity: _descOpacity,
                      child: Text(
                        l10n.letCameraGuideYouSubtitle,
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Color(0xFFEAC7FF),
                          fontSize: 16,
                          height: 1.4,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class ShimmerBorderBox extends StatelessWidget {
  const ShimmerBorderBox({
    super.key,
    required this.borderT,
    required this.width,
    required this.height,
    required this.child,
    this.radius = 30,
    this.borderWidth = 4,
    this.gap = 10,
  });

  final Animation<double> borderT;
  final double width;
  final double height;
  final Widget child;

  final double radius;
  final double borderWidth;
  final double gap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      height: height,
      child: AnimatedBuilder(
        animation: borderT,
        builder: (context, _) {
          final angle = borderT.value * math.pi * 2;

          return CustomPaint(
            painter: _ShimmerRingPainter(
              angle: angle,
              radius: radius,
              stroke: borderWidth,
            ),
            child: Padding(
              padding: EdgeInsets.all(gap + borderWidth / 2),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(radius - 4),
                child: child,
              ),
            ),
          );
        },
      ),
    );
  }
}

class _ShimmerRingPainter extends CustomPainter {
  _ShimmerRingPainter({
    required this.angle,
    required this.radius,
    required this.stroke,
  });

  final double angle;
  final double radius;
  final double stroke;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;

    final shader = SweepGradient(
      startAngle: angle,
      endAngle: angle + math.pi * 2,
      colors: const [
        Color(0x00FFFFFF), // прозрачный
        Color(0x99FFD0F0), // первый блик
        Color(0xFFFF7AC8), // второй блик (самый яркий)
        Color(0x66A78BFA), // хвост
        Color(0x00FFFFFF), // обратно в прозрачный
        Color(0x00FFFFFF), // чтобы конец соответствовал началц и не было остановки на стыке
      ],
      stops: const [0.00, 0.04, 0.08, 0.13, 0.20, 1.00],
    ).createShader(rect);

    final paint = Paint()
      ..shader = shader
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        rect.deflate(stroke / 2),
        Radius.circular(radius),
      ),
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant _ShimmerRingPainter oldDelegate) {
    return oldDelegate.angle != angle ||
        oldDelegate.radius != radius ||
        oldDelegate.stroke != stroke;
  }
}
