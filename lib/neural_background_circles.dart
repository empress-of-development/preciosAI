import 'dart:math';
import 'dart:ui';

import 'package:flutter/material.dart';

class NeuralNetworkWithBlurredCircles extends StatefulWidget {
  const NeuralNetworkWithBlurredCircles({super.key});

  @override
  State<NeuralNetworkWithBlurredCircles> createState() =>
      _NeuralNetworkWithBlurredCirclesState();
}

class _NeuralNetworkWithBlurredCirclesState
    extends State<NeuralNetworkWithBlurredCircles>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  // late Animation<double> _animation;
  List<Offset> points = [];
  final int pointCount = 150;
  final Random rnd = Random();

  @override
  void initState() {
    super.initState();

    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 20),
    )..repeat();

    //_animation = CurvedAnimation(parent: _controller, curve: Curves.easeInOutCubic);

    // создаём случайные точки для нейросети
    for (int i = 0; i < pointCount; i++) {
      points.add(Offset(rnd.nextDouble(), rnd.nextDouble()));
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Offset animatedPoint(Offset p, double t) {
    return Offset(
      (p.dx + sin(t + p.dx * 4) * 0.01).clamp(0, 1),
      (p.dy + cos(t + p.dy * 4) * 0.01).clamp(0, 1),
    );
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;

    return AnimatedBuilder(
      animation: _controller,
      builder: (_, __) {
        final double t = _controller.value * 2 * pi;
        final double shift = sin(_controller.value * 2 * pi) * 150;

        return Stack(
          children: [
            Positioned(
              left: screenWidth * -0.25 + shift,
              top: screenHeight * -0.1,
              child: _circle(Colors.indigo, screenWidth * 0.7),
            ),
            Positioned(
              right: screenWidth * -0.2 - shift,
              top: screenHeight * 0.7,
              child: _circle(Colors.indigo.shade400, screenWidth * 0.85),
            ),
            Positioned(
              left: screenWidth * 0.05,
              bottom: screenHeight * -0.2 + shift,
              child: _circle(Colors.indigo.shade900, screenWidth * 0.7),
            ),

            Positioned.fill(
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
                child: Container(color: Colors.transparent),
              ),
            ),

            CustomPaint(
              painter: _NeuralPainter(
                points: points.map((p) => animatedPoint(p, t)).toList(),
              ),
              child: Container(),
            ),
          ],
        );
      },
    );
  }

  Widget _circle(Color color, double size) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: color.withOpacity(0.85),
      ),
    );
  }
}

class _NeuralPainter extends CustomPainter {
  final List<Offset> points;
  _NeuralPainter({required this.points});

  @override
  void paint(Canvas canvas, Size size) {
    final Paint linePaint = Paint()
      ..color = Colors.black.withOpacity(0.95)
      ..strokeWidth = 3;

    final Paint nodePaint = Paint()
      ..color = Colors.black.withOpacity(0.6)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 1);

    for (int i = 0; i < points.length; i++) {
      final p1 = Offset(points[i].dx * size.width, points[i].dy * size.height);
      canvas.drawCircle(p1, 4, nodePaint);

      for (int j = i + 1; j < points.length; j++) {
        final p2 = Offset(
          points[j].dx * size.width,
          points[j].dy * size.height,
        );
        final double dist = (p1 - p2).distance;
        if (dist < size.width * 0.25) {
          final double opacity = (1 - dist / (size.width * 0.25)) * 0.1;
          canvas.drawLine(
            p1,
            p2,
            linePaint..color = Colors.black.withOpacity(opacity),
          );
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant _NeuralPainter oldDelegate) => true;
}
