import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:preciosai/l10n/app_localizations.dart';

class AnimatedIntroScreen2 extends StatefulWidget {
  const AnimatedIntroScreen2({
    super.key,
    required this.gridAsset,
    required this.isActive,
  });

  final String gridAsset;
  final bool isActive;

  @override
  State<AnimatedIntroScreen2> createState() => _AnimatedIntroScreen2State();
}

class _AnimatedIntroScreen2State extends State<AnimatedIntroScreen2>
    with TickerProviderStateMixin {
  late final AnimationController _c;
  late final AnimationController _traceC; // running highlight

  final _stackKey = GlobalKey();
  final _gridKey = GlobalKey();

  late final Animation<double> _gridIn;
  late final Animation<double> _circuitDraw;

  Rect? _gridRect;

  @override
  void initState() {
    super.initState();
    _c = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1650),
    );

    _traceC = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2400),
    );

    if (widget.isActive) {
      _c.forward(from: 0);
      _traceC.repeat();
    }

    Animation<double> tweenInterval(
      double begin,
      double end, {
      Curve curve = Curves.easeOutCubic,
    }) {
      return CurvedAnimation(
        parent: _c,
        curve: Interval(begin, end, curve: curve),
      ).drive(Tween(begin: 0, end: 1));
    }

    _gridIn = tweenInterval(0.28, 0.62, curve: Curves.easeOutBack);
    _circuitDraw = tweenInterval(0.35, 0.85, curve: Curves.easeOut);

    WidgetsBinding.instance.addPostFrameCallback((_) => _measureGridRect());
  }

  @override
  void didUpdateWidget(covariant AnimatedIntroScreen2 oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (!oldWidget.isActive && widget.isActive) {
      _c.forward(from: 0);
      _traceC.repeat();
    }
    if (oldWidget.isActive && !widget.isActive) {
      _c.stop();
      _c.reset();
      _traceC.stop();
    }

    WidgetsBinding.instance.addPostFrameCallback((_) => _measureGridRect());
  }

  void _measureGridRect() {
    final stackCtx = _stackKey.currentContext;
    final gridCtx = _gridKey.currentContext;
    if (stackCtx == null || gridCtx == null) return;

    final stackBox = stackCtx.findRenderObject() as RenderBox?;
    final gridBox = gridCtx.findRenderObject() as RenderBox?;
    if (stackBox == null || gridBox == null) return;

    final topLeft = gridBox.localToGlobal(Offset.zero, ancestor: stackBox);
    final rect = topLeft & gridBox.size;

    if (_gridRect != rect) {
      setState(() => _gridRect = rect);
    }
  }

  @override
  void dispose() {
    _c.dispose();
    _traceC.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    WidgetsBinding.instance.addPostFrameCallback((_) => _measureGridRect());
    final size = MediaQuery.sizeOf(context);

    return AnimatedBuilder(
      animation: _c,
      builder: (context, _) {
        return Stack(
          key: _stackKey,
          children: [
            const _PurpleBackground2(),

            SafeArea(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 18),
                child: Column(
                  children: [
                    const SizedBox(height: 12),

                    _Stagger(
                      controller: _c,
                      from: 0.02,
                      to: 0.20,
                      y: 12,
                      child: const _TopHeaderBars(),
                    ),

                    const SizedBox(height: 10),

                    Expanded(
                      flex: 56,
                      child: Center(
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxWidth: math.min(420, size.width),
                            maxHeight: 520,
                          ),
                          child: _SlideFadeScale(
                            t: _gridIn.value,
                            from: const Offset(0, 18),
                            scaleFrom: 0.92,
                            child: Stack(
                              children: [
                                _SpriteGridImage(
                                  asset: widget.gridAsset,
                                  corner: 18,
                                ),
                                Positioned.fill(
                                  child: IgnorePointer(
                                    child: CustomPaint(
                                      painter: _CircuitPainter(
                                        t: _circuitDraw.value,
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 6),

                    Expanded(
                      flex: 36,
                      child: Center(
                        child: FractionallySizedBox(
                          widthFactor: 0.7,
                          child: _Stagger(
                            controller: _c,
                            from: 0.48,
                            to: 1.00,
                            y: 18,
                            child: const _TextPanel(),
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 10),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _PurpleBackground2 extends StatelessWidget {
  const _PurpleBackground2();

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF2C0F63), Color(0xFF1B0A3D), Color(0xFF12072D)],
        ),
      ),
    );
  }
}

class _TopHeaderBars extends StatelessWidget {
  const _TopHeaderBars();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // left pink pill
        Expanded(
          child: Align(
            alignment: Alignment.centerLeft,
            child: Container(
              height: 22,
              width: MediaQuery.of(context).size.width / 3,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(999),
                gradient: const LinearGradient(
                  colors: [Color(0xFFFF6FB1), Color(0xFFFFA6CF)],
                ),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.25),
                    blurRadius: 14,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
            ),
          ),
        ),

        //const SizedBox(width: 12),

        // right white pill
        Expanded(
          child: Align(
            alignment: Alignment.centerRight,
            child: Container(
              height: 22,
              width: MediaQuery.of(context).size.width / 5 * 3,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(999),
                color: Colors.white.withOpacity(0.92),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.18),
                    blurRadius: 14,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _TextPanel extends StatelessWidget {
  const _TextPanel();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Stack(
      children: [
        // vector frame around text
        Positioned.fill(child: CustomPaint(painter: _TextFramePainter())),
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 6, 10, 6),
          child: Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Text(
                  l10n.findInspiration,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 28,
                    height: 1.05,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                    letterSpacing: -0.6,
                  ),
                ),
                const SizedBox(height: 14),
                Text(
                  l10n.findInspirationSubtitle,
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 16.2,
                    height: 1.25,
                    color: Colors.white.withOpacity(0.88),
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _TextFramePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final r = RRect.fromRectAndRadius(
      Rect.fromLTWH(6, 6, size.width - 12, size.height - 12),
      const Radius.circular(18),
    );

    final frame = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..color = const Color(0xFF6EFFC3).withOpacity(0.55)
      ..strokeJoin = StrokeJoin.round;

    final glow = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 7
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 8)
      ..color = const Color(0xFF6EFFC3).withOpacity(0.18);

    canvas.drawRRect(r, glow);
    canvas.drawRRect(r, frame);

    // corner “brackets”
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..color = const Color(0xFF6EFFC3).withOpacity(0.75)
      ..strokeCap = StrokeCap.round;

    const c = 18.0;
    // top-left
    canvas.drawLine(const Offset(18, 14), const Offset(18 + c, 14), p);
    canvas.drawLine(const Offset(14, 18), const Offset(14, 18 + c), p);
    // top-right
    canvas.drawLine(
      Offset(size.width - 18, 14),
      Offset(size.width - 18 - c, 14),
      p,
    );
    canvas.drawLine(
      Offset(size.width - 14, 18),
      Offset(size.width - 14, 18 + c),
      p,
    );
    // bottom-left
    canvas.drawLine(
      Offset(18, size.height - 14),
      Offset(18 + c, size.height - 14),
      p,
    );
    canvas.drawLine(
      Offset(14, size.height - 18),
      Offset(14, size.height - 18 - c),
      p,
    );
    // bottom-right
    canvas.drawLine(
      Offset(size.width - 18, size.height - 14),
      Offset(size.width - 18 - c, size.height - 14),
      p,
    );
    canvas.drawLine(
      Offset(size.width - 14, size.height - 18),
      Offset(size.width - 14, size.height - 18 - c),
      p,
    );

    // tiny decorative slashes bottom-left
    final deco = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2
      ..color = const Color(0xFF6EFFC3).withOpacity(0.55)
      ..strokeCap = StrokeCap.round;

    final y = size.height - 10;
    canvas.drawLine(Offset(56, y - 10), Offset(72, y - 2), deco);
    canvas.drawLine(Offset(66, y - 10), Offset(82, y - 2), deco);
    canvas.drawLine(Offset(76, y - 10), Offset(92, y - 2), deco);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _Stagger extends StatelessWidget {
  const _Stagger({
    required this.controller,
    required this.child,
    required this.from,
    required this.to,
    this.y = 20,
    this.scaleFrom = 1,
  });

  final AnimationController controller;
  final Widget child;
  final double from;
  final double to;
  final double y;
  final double scaleFrom;

  @override
  Widget build(BuildContext context) {
    final anim = CurvedAnimation(
      parent: controller,
      curve: Interval(from, to, curve: Curves.easeOutCubic),
    );

    return AnimatedBuilder(
      animation: anim,
      builder: (_, __) {
        return Opacity(
          opacity: anim.value,
          child: Transform.translate(
            offset: Offset(0, (1 - anim.value) * y),
            child: Transform.scale(
              scale: scaleFrom + (1 - scaleFrom) * anim.value,
              child: child,
            ),
          ),
        );
      },
    );
  }
}

class _SlideFadeScale extends StatelessWidget {
  const _SlideFadeScale({
    required this.t,
    required this.child,
    this.from = Offset.zero,
    this.scaleFrom = 0.94,
  });

  final double t;
  final Offset from;
  final double scaleFrom;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tt = t.clamp(0.0, 1.0);
    final eased = Curves.easeOutCubic.transform(tt);
    final opacity = eased.clamp(0.0, 1.0);

    return Opacity(
      opacity: opacity,
      child: Transform.translate(
        offset: Offset(from.dx * (1 - eased), from.dy * (1 - eased)),
        child: Transform.scale(
          scale: scaleFrom + (1 - scaleFrom) * eased,
          child: child,
        ),
      ),
    );
  }
}

class _CircuitPainter extends CustomPainter {
  _CircuitPainter({required this.t});

  final double t;

  @override
  void paint(Canvas canvas, Size size) {
    final neon = Paint()
      ..color = const Color(0xFF47FFB6).withOpacity(0.60)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2;

    final dot = Paint()
      ..color = const Color(0xFF47FFB6).withOpacity(0.90)
      ..style = PaintingStyle.fill;

    void drawPathAnimated(Path p) {
      final out = Path();
      for (final metric in p.computeMetrics()) {
        final len = metric.length;
        final take = (len * t).clamp(0.0, len);
        out.addPath(metric.extractPath(0, take), Offset.zero);
      }
      canvas.drawPath(out, neon);
    }

    final pad = 8.0;
    final r = RRect.fromRectAndRadius(
      Rect.fromLTWH(pad, pad, size.width - pad * 2, size.height - pad * 2),
      const Radius.circular(22),
    );

    final frame = Path()..addRRect(r);
    drawPathAnimated(frame);

    final cols = 3;
    final rows = 3;
    final gap = 12.0;
    final cellW = (size.width - pad * 2 - gap * (cols - 1)) / cols;
    final cellH = (size.height - pad * 2 - gap * (rows - 1)) / rows;

    Offset cellCenter(int row, int col) {
      final x = pad + col * (cellW + gap) + cellW / 2;
      final y = pad + row * (cellH + gap) + cellH / 2;
      return Offset(x, y);
    }

    final trace = Path();

    for (int r = 0; r < rows; r++) {
      final a = cellCenter(r, 0);
      final b = cellCenter(r, 1);
      final c = cellCenter(r, 2);
      trace
        ..moveTo(a.dx, a.dy)
        ..lineTo(b.dx, b.dy)
        ..lineTo(c.dx, c.dy);
    }

    for (int c = 0; c < cols; c++) {
      final a = cellCenter(0, c);
      final b = cellCenter(1, c);
      final d = cellCenter(2, c);
      trace
        ..moveTo(a.dx, a.dy)
        ..lineTo(b.dx, b.dy)
        ..lineTo(d.dx, d.dy);
    }

    drawPathAnimated(trace);

    final dots = <Offset>[];
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < cols; c++) {
        dots.add(cellCenter(r, c));
      }
    }

    final count = (dots.length * t).floor().clamp(0, dots.length);
    for (int i = 0; i < count; i++) {
      canvas.drawCircle(dots[i], 4.2, dot);
    }
  }

  @override
  bool shouldRepaint(covariant _CircuitPainter oldDelegate) =>
      oldDelegate.t != t;
}

class _SpriteGridImage extends StatelessWidget {
  const _SpriteGridImage({required this.asset, this.corner = 18});

  final String asset;
  final double corner;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(corner),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: FittedBox(fit: BoxFit.contain, child: Image.asset(asset)),
          ),
        ],
      ),
    );
  }
}
