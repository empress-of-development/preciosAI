import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

class ExpandableCornerImage extends StatefulWidget {
  final String imagePath;
  final double scaleFactor;

  const ExpandableCornerImage({
    super.key,
    required this.imagePath,
    this.scaleFactor = 2.0,
  });

  @override
  State<ExpandableCornerImage> createState() => _ExpandableCornerImageState();
}

class _ExpandableCornerImageState extends State<ExpandableCornerImage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnim;

  Future<Size>? _naturalSizeFuture;

  @override
  void initState() {
    super.initState();

    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 260),
    );

    _scaleAnim = Tween<double>(
      begin: 1.0,
      end: widget.scaleFactor,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOut));

    _naturalSizeFuture = _getImageNaturalSize(widget.imagePath);
  }

  @override
  void didUpdateWidget(covariant ExpandableCornerImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.imagePath != widget.imagePath) {
      _naturalSizeFuture = _getImageNaturalSize(widget.imagePath);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _toggle() {
    if (_controller.isAnimating) return;
    if (_controller.status == AnimationStatus.completed) {
      _controller.reverse();
    } else {
      _controller.forward();
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenW = MediaQuery.of(context).size.width;
    final baseWidth = screenW * 0.20;

    return FutureBuilder<Size>(
      future: _naturalSizeFuture,
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return Positioned(
            right: 16,
            bottom: 32,
            child: SizedBox(
              width: baseWidth,
              height: baseWidth,
              child: const ColoredBox(color: Colors.grey),
            ),
          );
        }

        final natural = snapshot.data!;
        final aspect = natural.width / natural.height;
        final baseHeight = baseWidth / aspect;

        return Positioned(
          right: 16,
          bottom: 32,
          child: AnimatedBuilder(
            animation: _scaleAnim,
            builder: (context, child) {
              return Transform.scale(
                scale: _scaleAnim.value,
                alignment: Alignment.bottomRight,
                child: child,
              );
            },
            child: GestureDetector(
              behavior: HitTestBehavior.translucent,
              onTap: _toggle,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: SizedBox(
                  width: baseWidth,
                  height: baseHeight,
                  child: widget.imagePath.startsWith('assets/')
                      ? Image.asset(widget.imagePath, fit: BoxFit.cover)
                      : Image.file(File(widget.imagePath), fit: BoxFit.cover),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Future<Size> _getImageNaturalSize(String path) {
    final completer = Completer<Size>();
    final image = widget.imagePath.startsWith('assets/')
        ? Image.asset(widget.imagePath, fit: BoxFit.cover)
        : Image.file(File(widget.imagePath), fit: BoxFit.cover);
    final provider = image.image;

    late final ImageStreamListener listener;
    listener = ImageStreamListener(
      (info, _) {
        final mySize = Size(
          info.image.width.toDouble(),
          info.image.height.toDouble(),
        );
        if (!completer.isCompleted) completer.complete(mySize);
        provider.resolve(const ImageConfiguration()).removeListener(listener);
      },
      onError: (err, stack) {
        if (!completer.isCompleted) completer.complete(const Size(100, 100));
        provider.resolve(const ImageConfiguration()).removeListener(listener);
      },
    );

    provider.resolve(const ImageConfiguration()).addListener(listener);

    // safety timeout
    Future.delayed(const Duration(seconds: 2)).then((_) {
      if (!completer.isCompleted) completer.complete(const Size(100, 100));
    });

    return completer.future;
  }
}
