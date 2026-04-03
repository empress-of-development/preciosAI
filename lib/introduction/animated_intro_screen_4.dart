import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:preciosai/l10n/app_localizations.dart';

class AnimatedIntroScreen4 extends StatefulWidget {
  const AnimatedIntroScreen4({
    super.key,
    required this.centerAsset,
    required this.isActive,
  });

  final String centerAsset;
  final bool isActive;

  @override
  State<AnimatedIntroScreen4> createState() => _AnimatedIntroScreen4State();
}

class _AnimatedIntroScreen4State extends State<AnimatedIntroScreen4>
    with TickerProviderStateMixin {
  late final AnimationController _staggerC;
  late final AnimationController _glowC;

  late final Animation<double> _imageOpacity;
  late final Animation<double> _imageScaleIn;

  late final Animation<double> _titleOpacity;
  late final Animation<Offset> _titleSlide;

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

    // continuous pulsing glow
    _glowC = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );

    _imageOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.05, 0.45, curve: Curves.easeOut),
    );
    _imageScaleIn = Tween(begin: 0.96, end: 1.0).animate(
      CurvedAnimation(
        parent: _staggerC,
        curve: const Interval(0.05, 0.50, curve: Curves.easeOutBack),
      ),
    );

    _titleOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.35, 0.75, curve: Curves.easeOut),
    );
    _titleSlide = Tween(begin: const Offset(0, 0.18), end: Offset.zero).animate(
      CurvedAnimation(
        parent: _staggerC,
        curve: const Interval(0.35, 0.80, curve: Curves.easeOutCubic),
      ),
    );

    _descOpacity = CurvedAnimation(
      parent: _staggerC,
      curve: const Interval(0.55, 1.00, curve: Curves.easeOut),
    );
    _descSlide = Tween(begin: const Offset(0, 0.16), end: Offset.zero).animate(
      CurvedAnimation(
        parent: _staggerC,
        curve: const Interval(0.55, 1.00, curve: Curves.easeOutCubic),
      ),
    );

    _resolveAssetAspectRatio(widget.centerAsset);

    if (widget.isActive) _start();
  }

  void _start() {
    _staggerC.forward(from: 0);
    _glowC.repeat(reverse: true);
  }

  @override
  void didUpdateWidget(covariant AnimatedIntroScreen4 oldWidget) {
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
      _glowC.stop();
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
        debugPrint('Failed to resolve asset size: $error');
        stream.removeListener(listener);
      },
    );

    stream.addListener(listener);
  }

  @override
  void dispose() {
    _staggerC.dispose();
    _glowC.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ar = _assetAspectRatio ?? (9 / 16);
    final l10n = AppLocalizations.of(context)!;

    return LayoutBuilder(
      builder: (context, c) {
        final w = c.maxWidth;
        final h = c.maxHeight;

        final maxArtH = h * 0.62;
        final artWByScreen = w * 0.86;
        final artHFromW = artWByScreen / ar;

        final artH = math.min(maxArtH, artHFromW);

        return Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFF2C0F63), Color(0xFF1B0A3D), Color(0xFF12072D)],
            ),
          ),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 22),
              child: Column(
                children: [
                  const SizedBox(height: 10),

                  Center(
                    child: FadeTransition(
                      opacity: _imageOpacity,
                      child: ScaleTransition(
                        scale: _imageScaleIn,
                        child: LayoutBuilder(
                          builder: (context, c) {
                            // ограничиваем картинку по высоте, чтобы она не выталкивала текст вниз
                            final maxArtH =
                                MediaQuery.sizeOf(context).height * 0.58;
                            final maxArtW = c.maxWidth * 0.92;

                            return ConstrainedBox(
                              constraints: BoxConstraints(
                                maxWidth: maxArtW,
                                maxHeight: maxArtH,
                              ),
                              child: _PulsingGlow(
                                t: _glowC,
                                width: maxArtW,
                                height: maxArtH,
                                child: FittedBox(
                                  fit: BoxFit.contain,
                                  child: Image.asset(widget.centerAsset),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 6),

                  SlideTransition(
                    position: _titleSlide,
                    child: FadeTransition(
                      opacity: _titleOpacity,
                      child: _GradientTitle(
                        leftText: l10n.yourPhotos,
                        rightText: l10n.stayYours,
                      ),
                    ),
                  ),

                  const SizedBox(height: 6),

                  SlideTransition(
                    position: _descSlide,
                    child: FadeTransition(
                      opacity: _descOpacity,
                      child: Text(
                        l10n.privacySubtitle,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.82),
                          fontSize: 16,
                          height: 1.32,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ),

                  const Spacer(),

                  const SizedBox(height: 10),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _PulsingGlow extends StatelessWidget {
  const _PulsingGlow({
    required this.t,
    required this.width,
    required this.height,
    required this.child,
  });

  final Animation<double> t;
  final double width;
  final double height;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final radius = 28.0;

    return AnimatedBuilder(
      animation: t,
      builder: (context, _) {
        final v = Curves.easeInOut.transform(t.value);
        final blur = 22.0 + 26.0 * v;
        final spread = 1.0 + 2.2 * v;
        final glowOpacity = 0.18 + 0.20 * v;

        return SizedBox(
          width: width,
          height: height,
          child: Stack(
            alignment: Alignment.center,
            children: [
              // soft radial wash
              IgnorePointer(
                child: Container(
                  width: width * (1.05 + 0.04 * v),
                  height: height * (1.05 + 0.04 * v),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(radius + 6),
                    gradient: RadialGradient(
                      radius: 0.85,
                      colors: [
                        const Color(0xFFFF7AC8).withOpacity(0.10 + 0.10 * v),
                        const Color(0xFF9B7CFF).withOpacity(0.06 + 0.08 * v),
                        Colors.transparent,
                      ],
                      stops: const [0.0, 0.55, 1.0],
                    ),
                  ),
                ),
              ),

              // main glow container (shadow-based)
              Container(
                width: width,
                height: height,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(radius),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFFFF7AC8).withOpacity(glowOpacity),
                      blurRadius: blur,
                      spreadRadius: spread,
                    ),
                    BoxShadow(
                      color: const Color(
                        0xFF9B7CFF,
                      ).withOpacity(glowOpacity * 0.85),
                      blurRadius: blur * 0.9,
                      spreadRadius: spread * 0.8,
                    ),
                  ],
                ),
              ),

              child,
            ],
          ),
        );
      },
    );
  }
}

class _GradientTitle extends StatelessWidget {
  const _GradientTitle({required this.leftText, required this.rightText});

  final String leftText;
  final String rightText;

  @override
  Widget build(BuildContext context) {
    return RichText(
      textAlign: TextAlign.center,
      text: TextSpan(
        style: const TextStyle(
          fontSize: 34,
          height: 1.05,
          fontWeight: FontWeight.w800,
          letterSpacing: -0.4,
        ),
        children: [
          TextSpan(
            text: leftText,
            style: const TextStyle(color: Color(0xFFFF7AC8)),
          ),
          TextSpan(
            text: rightText,
            style: TextStyle(color: Colors.white.withOpacity(0.95)),
          ),
        ],
      ),
    );
  }
}
