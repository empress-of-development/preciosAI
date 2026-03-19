import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'album_page.dart';
import 'camera_page.dart';
import 'glowing_button.dart';
import 'neural_background_circles.dart';

class ResultPage extends StatefulWidget {
  final List<Uint8List> bytes;
  final String refImagePath;
  const ResultPage({
    super.key,
    required this.bytes,
    required this.refImagePath,
  });

  @override
  State<ResultPage> createState() => _ResultPageState();
}

class _ResultPageState extends State<ResultPage> {
  bool _isZoomed = false;
  Uint8List? refImage;

  bool _loading = true;
  int _currentIndex = 0;

  final PageController _pageController = PageController();

  @override
  void initState() {
    super.initState();
    _loadRefImage();
  }

  Future<void> _loadRefImage() async {
    refImage = await _loadAssetBytes();
    setState(() {
      _loading = false;
    });
  }

  Future<Uint8List> _loadAssetBytes() async {
    if (widget.refImagePath.startsWith('assets/')) {
      final data = await rootBundle.load(widget.refImagePath);
      return data.buffer.asUint8List();
    } else {
      return await File(widget.refImagePath).readAsBytes();
    }
  }

  void _continue(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const AlbumScreen()),
    );
  }

  void _retry(BuildContext context) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => CameraPage(refImagePath: widget.refImagePath),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          const NeuralNetworkWithBlurredCircles(),

          PreferredSize(
            preferredSize: Size.fromHeight(kToolbarHeight * 1.3),
            child: ClipRRect(
              borderRadius: const BorderRadius.only(
                bottomLeft: Radius.circular(24),
                bottomRight: Radius.circular(24),
              ),
              child: Container(
                color: Colors.white.withOpacity(0.35),
                child: SafeArea(
                  bottom: false,
                  child: Container(
                    height: kToolbarHeight * 1.3,
                    alignment: Alignment.center,
                    child: Text(
                      'Nice work!',
                      style: TextStyle(
                        color: Colors.grey.shade900,
                        fontSize: 26,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),

          Center(
            child: Hero(
              tag: 'res_image',
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
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      maxWidth: MediaQuery.of(context).size.width * 0.9,
                      maxHeight: MediaQuery.of(context).size.height * 0.6,
                    ),
                    child: PageView.builder(
                      controller: _pageController,
                      physics: _isZoomed
                          ? const NeverScrollableScrollPhysics()
                          : const PageScrollPhysics(),
                      itemCount: widget.bytes.length,
                      onPageChanged: (index) {
                        setState(() => _currentIndex = index);
                      },
                      itemBuilder: (context, index) {
                        return _ZoomableComparison(
                          before: refImage!,
                          after: widget.bytes[index],
                          onZoomChanged: (zoomed) {
                            if (_isZoomed != zoomed)
                              setState(() => _isZoomed = zoomed);
                          },
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            bottom: 250,
            left: 0,
            right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                widget.bytes.length,
                (index) => Container(
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  width: _currentIndex == index ? 10 : 6,
                  height: _currentIndex == index ? 10 : 6,
                  decoration: BoxDecoration(
                    color: _currentIndex == index
                        ? Colors.deepPurpleAccent
                        : Colors.white54,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ),
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Padding(
                  padding: const EdgeInsets.only(bottom: 32),
                  child: RippleCircleButton(
                    iconPath: 'assets/icons/replay.png',
                    onTap: () => _retry(context),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(bottom: 32),
                  child: RippleCircleButton(
                    iconPath: 'assets/icons/continue.png',
                    onTap: () => _continue(context),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ZoomableComparisonState extends State<_ZoomableComparison> {
  final TransformationController _controller = TransformationController();
  double _divider = 0.5;

  ui.Image? _before;
  ui.Image? _after;

  @override
  void initState() {
    super.initState();
    _loadImages();

    _controller.addListener(() {
      widget.onZoomChanged(_controller.value.getMaxScaleOnAxis() > 1.01);
    });
  }

  Future<ui.Image> decodeImage(Uint8List bytes) async {
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    return frame.image;
  }

  Future<void> _loadImages() async {
    final before = await decodeImage(widget.before);
    final after = await decodeImage(widget.after);

    if (!mounted) return;

    setState(() {
      _before = before;
      _after = after;
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _before?.dispose();
    _after?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_before == null || _after == null) {
      return const Center(child: CircularProgressIndicator());
    }

    return InteractiveViewer(
      transformationController: _controller,
      clipBehavior: Clip.hardEdge,
      minScale: 1,
      maxScale: 4,
      child: LayoutBuilder(
        builder: (context, constraints) {
          return Stack(
            children: [
              CustomPaint(
                size: Size.infinite,
                painter: _ComparisonPainter(
                  before: _before!,
                  after: _after!,
                  divider: _divider,
                ),
              ),

              Positioned(
                left: constraints.maxWidth * _divider - 20,
                top: 0,
                bottom: 0,
                child: GestureDetector(
                  behavior: HitTestBehavior.translucent,
                  onHorizontalDragUpdate: (details) {
                    final box = context.findRenderObject() as RenderBox;
                    final dx = box.globalToLocal(details.globalPosition).dx;

                    setState(() {
                      _divider = (dx / constraints.maxWidth).clamp(0.0, 1.0);
                    });
                  },
                  child: Center(
                    child: Container(
                      width: 40,
                      height: 40,
                      decoration: const BoxDecoration(
                        color: Colors.black54,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.drag_handle, color: Colors.white),
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _ComparisonPainter extends CustomPainter {
  final ui.Image before;
  final ui.Image after;
  final double divider;

  _ComparisonPainter({
    required this.before,
    required this.after,
    required this.divider,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint();

    final beforeSize = Size(before.width.toDouble(), before.height.toDouble());
    final afterSize = Size(after.width.toDouble(), after.height.toDouble());

    final fittedBefore = applyBoxFit(BoxFit.contain, beforeSize, size);
    final fittedAfter = applyBoxFit(BoxFit.contain, afterSize, size);

    final dstRectBefore = Alignment.center.inscribe(
      fittedBefore.destination,
      Offset.zero & size,
    );
    final dstRectAfter = Alignment.center.inscribe(
      fittedAfter.destination,
      Offset.zero & size,
    );

    final srcRectBefore = Offset.zero & beforeSize;
    final srcRectAfter = Offset.zero & afterSize;

    canvas.drawImageRect(before, srcRectBefore, dstRectBefore, paint);

    canvas.save();

    final clipWidth = size.width * divider;
    canvas.clipRect(
      Rect.fromLTWH(0, 0, clipWidth, size.height),
    );

    canvas.drawImageRect(after, srcRectAfter, dstRectAfter, paint);
    canvas.restore();

    canvas.drawRect(
      Rect.fromLTWH(clipWidth - 1, 0, 2, size.height),
      Paint()..color = Colors.white,
    );
  }

  @override
  bool shouldRepaint(covariant _ComparisonPainter old) =>
      old.divider != divider || old.before != before || old.after != after;
}


class _ZoomableComparison extends StatefulWidget {
  final Uint8List before;
  final Uint8List after;
  final ValueChanged<bool> onZoomChanged;

  const _ZoomableComparison({
    required this.before,
    required this.after,
    required this.onZoomChanged,
  });

  @override
  State<_ZoomableComparison> createState() => _ZoomableComparisonState();
}
