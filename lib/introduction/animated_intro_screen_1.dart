import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:preciosai/l10n/app_localizations.dart';

class AnimatedIntroScreen1 extends StatefulWidget {
  const AnimatedIntroScreen1({
    super.key,
    required this.beforeModelAsset,
    required this.afterModelAsset,
    required this.isActive,
  });

  final String beforeModelAsset;
  final String afterModelAsset;
  final bool isActive;

  @override
  State<AnimatedIntroScreen1> createState() => _AnimatedIntroScreen1State();
}

class _AnimatedIntroScreen1State extends State<AnimatedIntroScreen1>
    with TickerProviderStateMixin {
  late final AnimationController _c;

  final _stackKey = GlobalKey();
  final _beforeKey = GlobalKey();
  final _afterKey = GlobalKey();

  Rect? _beforeRect;
  Rect? _afterRect;

  @override
  void initState() {
    super.initState();

    _c = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    );

    if (widget.isActive) {
      _c.forward(from: 0);
    }
  }

  @override
  void didUpdateWidget(covariant AnimatedIntroScreen1 oldWidget) {
    super.didUpdateWidget(oldWidget);

    // страница стала активной
    if (!oldWidget.isActive && widget.isActive) {
      _c.forward(from: 0);
    }

    // страница стала неактивной
    if (oldWidget.isActive && !widget.isActive) {
      _c.stop();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  void _measureRects() {
    final stackCtx = _stackKey.currentContext;
    final beforeCtx = _beforeKey.currentContext;
    final afterCtx = _afterKey.currentContext;
    if (stackCtx == null || beforeCtx == null || afterCtx == null) return;

    final stackBox = stackCtx.findRenderObject() as RenderBox?;
    final beforeBox = beforeCtx.findRenderObject() as RenderBox?;
    final afterBox = afterCtx.findRenderObject() as RenderBox?;
    if (stackBox == null || beforeBox == null || afterBox == null) return;

    final beforeTopLeft = beforeBox.localToGlobal(
      Offset.zero,
      ancestor: stackBox,
    );
    final afterTopLeft = afterBox.localToGlobal(
      Offset.zero,
      ancestor: stackBox,
    );

    final newBefore = beforeTopLeft & beforeBox.size;
    final newAfter = afterTopLeft & afterBox.size;

    if (_beforeRect != newBefore || _afterRect != newAfter) {
      setState(() {
        _beforeRect = newBefore;
        _afterRect = newAfter;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    WidgetsBinding.instance.addPostFrameCallback((_) => _measureRects());
    final l10n = AppLocalizations.of(context)!;
    final size = MediaQuery.sizeOf(context);

    return Stack(
      key: _stackKey,
      children: [
        _PurpleTechBackground(beforeRect: _beforeRect, afterRect: _afterRect),

        Positioned.fill(
          child: IgnorePointer(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: const Alignment(0, 0.2),
                  radius: 1.1,
                  colors: [Colors.transparent, Colors.black.withOpacity(0.35)],
                ),
              ),
            ),
          ),
        ),

        SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18),
            child: Column(
              children: [
                const SizedBox(height: 14),

                Expanded(
                  flex: 54,
                  child: LayoutBuilder(
                    builder: (context, c) {
                      final w = c.maxWidth;
                      final h = c.maxHeight;
                      const frameTop = 56.0;
                      const pillInsetTop = 10.0;

                      return Stack(
                        children: [
                          Positioned(
                            left: 0,
                            top: frameTop,
                            width: w * 0.48,
                            height: h - frameTop,
                            child: _Stagger(
                              controller: _c,
                              from: 0.18,
                              to: 0.38,
                              child: _BracketFrame(key: _beforeKey),
                            ),
                          ),
                          Positioned(
                            right: 0,
                            top: frameTop,
                            width: w * 0.48,
                            height: h - frameTop,
                            child: _Stagger(
                              controller: _c,
                              from: 0.22,
                              to: 0.42,
                              child: _BracketFrame(key: _afterKey),
                            ),
                          ),

                          Positioned(
                            left: 0,
                            top: frameTop + 22,
                            width: w * 0.48,
                            height: h - (frameTop + 22),
                            child: _Stagger(
                              controller: _c,
                              from: 0.28,
                              to: 0.62,
                              child: _ModelImage(
                                asset: widget.beforeModelAsset,
                                align: Alignment.topCenter,
                                extraTransform: Matrix4.identity()
                                  ..rotateZ(-0.02)
                                  ..translate(-2.0, 0.0),
                              ),
                            ),
                          ),
                          Positioned(
                            right: 0,
                            top: frameTop + 22,
                            width: w * 0.48,
                            height: h - (frameTop + 22),
                            child: _Stagger(
                              controller: _c,
                              from: 0.34,
                              to: 0.68,
                              child: _ModelImage(
                                asset: widget.afterModelAsset,
                                align: Alignment.topCenter,
                                extraTransform: Matrix4.identity()
                                  ..rotateZ(0.02)
                                  ..translate(2.0, 0.0),
                              ),
                            ),
                          ),

                          Positioned(
                            left: 0,
                            top: frameTop + pillInsetTop,
                            width: w * 0.48,
                            child: _Stagger(
                              controller: _c,
                              from: 0.05,
                              to: 0.22,
                              y: 10,
                              child: Center(
                                child: _PillLabel(text: l10n.before),
                              ),
                            ),
                          ),
                          Positioned(
                            right: 0,
                            top: frameTop + pillInsetTop,
                            width: w * 0.48,
                            child: _Stagger(
                              controller: _c,
                              from: 0.10,
                              to: 0.27,
                              y: 10,
                              child: Center(
                                child: _PillLabel(text: l10n.after),
                              ),
                            ),
                          ),
                        ],
                      );
                    },
                  ),
                ),

                const SizedBox(height: 10),

                Expanded(
                  flex: 26,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _Stagger(
                        controller: _c,
                        from: 0.52,
                        to: 0.78,
                        y: 16,
                        child: Text(
                          l10n.picturePerfectTitle,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            fontSize: 32,
                            height: 1.05,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -0.6,
                            color: Colors.white,
                          ),
                        ),
                      ),
                      // const SizedBox(height: 14),
                      _Stagger(
                        controller: _c,
                        from: 0.62,
                        to: 0.92,
                        y: 18,
                        child: Text(
                          l10n.picturePerfectSubtitle,
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 16.5,
                            height: 1.35,
                            color: const Color(0xFFFF6FB1).withOpacity(0.92),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(height: math.max(16, size.height * 0.02)),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _Stagger extends StatelessWidget {
  const _Stagger({
    required this.controller,
    required this.child,
    required this.from,
    required this.to,
    this.y = 22,
    this.x = 0,
    this.scaleFrom = 0.96,
  });

  final AnimationController controller;
  final Widget child;
  final double from;
  final double to;
  final double x;
  final double y;
  final double scaleFrom;

  @override
  Widget build(BuildContext context) {
    final curved = CurvedAnimation(
      parent: controller,
      curve: Interval(from, to, curve: Curves.easeOutCubic),
    );

    return AnimatedBuilder(
      animation: curved,
      builder: (context, _) {
        final t = curved.value;
        final opacity = t;
        final dx = (1 - t) * x;
        final dy = (1 - t) * y;
        final scale = scaleFrom + (1 - scaleFrom) * t;

        return Opacity(
          opacity: opacity,
          child: Transform.translate(
            offset: Offset(dx, dy),
            child: Transform.scale(scale: scale, child: child),
          ),
        );
      },
      child: child,
    );
  }
}

class _PurpleTechBackground extends StatelessWidget {
  const _PurpleTechBackground({
    required this.beforeRect,
    required this.afterRect,
  });

  final Rect? beforeRect;
  final Rect? afterRect;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      foregroundPainter: _TechBgPainter(
        beforeRect: beforeRect,
        afterRect: afterRect,
      ),
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF2C0F63), Color(0xFF1B0A3D), Color(0xFF12072D)],
          ),
        ),
      ),
    );
  }
}

class _TechBgPainter extends CustomPainter {
  _TechBgPainter({required this.beforeRect, required this.afterRect});

  final Rect? beforeRect;
  final Rect? afterRect;

  @override
  void paint(Canvas canvas, Size size) {
    if (beforeRect == null || afterRect == null) return;

    final b = beforeRect!;
    final a = afterRect!;
    final midX = (b.center.dx + a.center.dx) / 2;

    final clipBottom = b.bottom + 40;
    canvas.save();
    canvas.clipRect(Rect.fromLTRB(0, 0, size.width, clipBottom));

    final stroke = Paint()
      ..color = const Color(0xFFCAB8FF).withOpacity(0.20)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final glow = Paint()
      ..color = const Color(0xFFCAB8FF).withOpacity(0.08)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8.0
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8)
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final viaFill = Paint()
      ..color = const Color(0xFFCAB8FF).withOpacity(0.16)
      ..style = PaintingStyle.fill;

    final viaRing = Paint()
      ..color = Colors.white.withOpacity(0.10)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.1;

    void draw(Path p) {
      canvas.drawPath(p, glow);
      canvas.drawPath(p, stroke);
    }

    void via(Offset o, {double r = 3.8}) {
      canvas.drawCircle(o, r, viaFill);
      canvas.drawCircle(o, r + 1.6, viaRing);
    }

    final topY = 18.0; // прямо у верха
    final topLeft = size.width * 0.06;
    final topKnee1 = size.width * 0.26;
    final topDropX = size.width * 0.34;
    final topRight = size.width * 0.70;

    const topGap = 9.0;
    for (int i = 0; i < 3; i++) {
      final y = topY + i * topGap;
      final y2 = y + 22;

      final p = Path()
        ..moveTo(topLeft, y)
        ..lineTo(topKnee1, y)
        ..quadraticBezierTo(topDropX, y, topDropX, y2)
        ..lineTo(topRight, y2);

      draw(p);

      // Узлы в похожих местах
      if (i == 1) via(Offset(topKnee1, y), r: 4.0);
      if (i == 0) via(Offset(topDropX, y2), r: 3.9);
      if (i == 2) via(Offset(topRight, y2), r: 4.1);
    }

    final combX = size.width * 0.43;
    final comb = Path()
      ..moveTo(combX, topY - 10)
      ..lineTo(combX, topY + 10)
      ..lineTo(combX + 56, topY + 10);
    draw(comb);
    via(Offset(combX, topY + 10), r: 3.6);

    final divider = Paint()
      ..color = Colors.white.withOpacity(0.13)
      ..strokeWidth = 1.5;

    canvas.drawLine(
      Offset(midX, b.top + 10),
      Offset(midX, b.bottom - 10),
      divider,
    );

    final botBaseY = b.bottom + 6;
    final botLeft = b.left - 14;
    final botKnee1 = b.left + b.width * 0.62;
    final botDropX = midX - 8;
    final botRight = a.right + 26;

    const botGap = 9.0;
    for (int i = 0; i < 3; i++) {
      final y = botBaseY + i * botGap;
      final y2 = y + 22;

      final p = Path()
        ..moveTo(botLeft, y)
        ..lineTo(botKnee1, y)
        ..quadraticBezierTo(botDropX, y, botDropX, y2)
        ..lineTo(botRight, y2);

      draw(p);

      if (i == 1) via(Offset(botKnee1, y), r: 4.0);
      if (i == 0) via(Offset(botDropX, y2), r: 3.9);
      if (i == 2) via(Offset(botRight, y2), r: 4.1);
    }

    final hub = Offset(a.left + a.width * 0.30, b.bottom + 80);
    via(hub, r: 4.2);

    final branch = Path()
      ..moveTo(hub.dx - 130, hub.dy - 6)
      ..lineTo(hub.dx - 16, hub.dy - 6)
      ..quadraticBezierTo(hub.dx + 12, hub.dy - 6, hub.dx + 18, hub.dy + 14)
      ..lineTo(hub.dx + 175, hub.dy + 14);

    draw(branch);
    via(Offset(hub.dx - 16, hub.dy - 6), r: 3.6);
    via(Offset(hub.dx + 18, hub.dy + 14), r: 3.6);
    via(Offset(hub.dx + 94, hub.dy + 14), r: 3.3);

    final pinPaint = Paint()
      ..color = const Color(0xFFCAB8FF).withOpacity(0.14)
      ..strokeWidth = 2.0
      ..strokeCap = StrokeCap.round;

    final pinTop = 64.0;
    final pinX0 = size.width * 0.82;
    for (int i = 0; i < 6; i++) {
      final x = pinX0 + i * 10.0;
      canvas.drawLine(Offset(x, pinTop), Offset(x, pinTop + 18), pinPaint);
      canvas.drawCircle(Offset(x, pinTop + 18), 2.5, viaFill);
    }

    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _TechBgPainter oldDelegate) {
    return oldDelegate.beforeRect != beforeRect ||
        oldDelegate.afterRect != afterRect;
  }
}

class _PillLabel extends StatelessWidget {
  const _PillLabel({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        gradient: const LinearGradient(
          colors: [Color(0xFFFF7AB8), Color(0xFFFFB4D3)],
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.25),
            blurRadius: 18,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Color(0xFF2B0B4A),
          fontWeight: FontWeight.w900,
          letterSpacing: 1.2,
        ),
      ),
    );
  }
}

class _BracketFrame extends StatelessWidget {
  const _BracketFrame({super.key});

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _BracketPainter(),
      child: const SizedBox.expand(),
    );
  }
}

class _BracketPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = Colors.white.withOpacity(0.20)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;

    const corner = 18.0;

    // top-left
    canvas.drawPath(
      Path()
        ..moveTo(0, corner)
        ..lineTo(0, 0)
        ..lineTo(corner, 0),
      p,
    );

    // top-right
    canvas.drawPath(
      Path()
        ..moveTo(size.width - corner, 0)
        ..lineTo(size.width, 0)
        ..lineTo(size.width, corner),
      p,
    );

    // bottom-left
    canvas.drawPath(
      Path()
        ..moveTo(0, size.height - corner)
        ..lineTo(0, size.height)
        ..lineTo(corner, size.height),
      p,
    );

    // bottom-right
    canvas.drawPath(
      Path()
        ..moveTo(size.width - corner, size.height)
        ..lineTo(size.width, size.height)
        ..lineTo(size.width, size.height - corner),
      p,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _ModelImage extends StatelessWidget {
  const _ModelImage({
    required this.asset,
    required this.align,
    required this.extraTransform,
    this.scale = 0.85,
  });

  final String asset;
  final Alignment align;
  final Matrix4 extraTransform;
  final double scale;

  @override
  Widget build(BuildContext context) {
    return Transform(
      alignment: Alignment.center,
      transform: extraTransform,
      child: Align(
        alignment: align,
        child: Transform.scale(
          scale: scale,
          child: Image.asset(
            asset,
            fit: BoxFit.contain,
            filterQuality: FilterQuality.high,
          ),
        ),
      ),
    );
  }
}
