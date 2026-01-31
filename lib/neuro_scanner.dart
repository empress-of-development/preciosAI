import 'dart:io';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'camera_page.dart';

class ImageScanPage extends StatefulWidget {
  final String imagePath;

  const ImageScanPage({super.key, required this.imagePath});

  @override
  State<ImageScanPage> createState() => _ImageScanPageState();
}

class _ImageScanPageState extends State<ImageScanPage>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  List<Offset> points = [];
  final int pointCount = 150;
  final Random rnd = Random();
  final platform = const MethodChannel('photo_capture_channel_default');

  @override
  void initState() {
    super.initState();

    for (int i = 0; i < pointCount; i++) {
      points.add(Offset(rnd.nextDouble(), rnd.nextDouble()));
    }

    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    );

    _controller.addStatusListener((status) {
      if (status == AnimationStatus.completed) {
        _continue();
      }
    });

    _controller.forward();
  }

  void _continue() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(
        builder: (_) => CameraPage(refImagePath: widget.imagePath),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          Center(
            child: Hero(
              tag: 'neuro_scanner',
              child: Material(
                color: Colors.transparent,
                child: Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: Colors.transparent,
                    borderRadius: BorderRadius.circular(22),
                    border: Border.all(
                      color: Colors.deepPurpleAccent,
                      width: 4,
                    ),
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(18),
                    child: InteractiveViewer(
                      child: widget.imagePath.startsWith('assets/')
                          ? Image.asset(widget.imagePath, fit: BoxFit.cover)
                          : Image.file(
                              File(widget.imagePath),
                              fit: BoxFit.cover,
                            ),
                    ),
                  ),
                ),
              ),
            ),
          ),

          AnimatedBuilder(
            animation: _controller,
            builder: (_, __) {
              final double scanY = _controller.value;

              return CustomPaint(
                painter: _ScanNeuralPainter(points: points, scanY: scanY),
                child: Container(),
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ScanNeuralPainter extends CustomPainter {
  final List<Offset> points;
  final double scanY;

  _ScanNeuralPainter({required this.points, required this.scanY});

  @override
  void paint(Canvas canvas, Size size) {
    final Paint linePaint = Paint()
      ..strokeWidth = 1.5
      ..color = Colors.black.withOpacity(0.5);

    const double scanHeight = 0.4;
    final double visibleBottom = scanY * size.height;
    final double visibleTop = visibleBottom - scanHeight * size.height;

    for (int i = 0; i < points.length; i++) {
      final p = Offset(points[i].dx * size.width, points[i].dy * size.height);

      if (p.dy < visibleTop || p.dy > visibleBottom) continue;

      final double opacity =
          ((p.dy - visibleTop) / (visibleBottom - visibleTop)).clamp(0.0, 1.0);
      final Paint nodePaint = Paint()
        ..color = Colors.black.withOpacity(1.0 * opacity);
      //..maskFilter = const MaskFilter.blur(BlurStyle.normal, 1);

      canvas.drawCircle(p, 4, nodePaint);

      for (int j = i + 1; j < points.length; j++) {
        final p2 = Offset(
          points[j].dx * size.width,
          points[j].dy * size.height,
        );
        if (p2.dy < visibleTop || p2.dy > visibleBottom) continue;

        final double dist = (p - p2).distance;
        if (dist < size.width * 0.25) {
          final double lineOpacity =
              (1 - dist / (size.width * 0.25)) * 0.7 * opacity;
          canvas.drawLine(
            p,
            p2,
            linePaint..color = linePaint.color.withOpacity(lineOpacity),
          );
        }
      }
    }

    // градиентная полоска сканера
    final double gradientHeight = scanHeight * size.height;
    const double sharpLineHeight = 4.0;
    final Rect gradientRect = Rect.fromLTWH(
      0,
      visibleTop,
      size.width,
      gradientHeight,
    );

    final Paint scanPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          Colors.indigoAccent.withOpacity(0.0),
          Colors.indigoAccent.withOpacity(0.15),
          Colors.indigoAccent.withOpacity(0.4),
          Colors.indigoAccent.withOpacity(1.0),
        ],
        stops: [0.0, 0.5, 0.85, 1.0],
      ).createShader(gradientRect);

    canvas.drawRect(gradientRect, scanPaint);

    // дополнительная чёткая полоска снизу градиента
    final Paint sharpLine = Paint()
      ..color = Colors.indigoAccent.withOpacity(1.0);
    canvas.drawRect(
      Rect.fromLTWH(
        0,
        visibleBottom - sharpLineHeight,
        size.width,
        sharpLineHeight,
      ),
      sharpLine,
    );
  }

  @override
  bool shouldRepaint(covariant _ScanNeuralPainter oldDelegate) => true;
}
