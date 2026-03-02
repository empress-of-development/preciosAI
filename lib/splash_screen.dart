import 'dart:async';

import 'package:flutter/material.dart';
import 'package:preciosai/onboarding_gate.dart';
import 'package:video_player/video_player.dart';

class SplashScreenVideo extends StatefulWidget {
  const SplashScreenVideo({super.key});

  @override
  State<SplashScreenVideo> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreenVideo> {
  late final VideoPlayerController _controller;

  double _opacity = 1.0;

  bool _isFinished = false;
  bool _isNavigating = false;

  Timer? _fadeTimer;
  Timer? _continueTimer;

  static const Duration _fadeDuration = Duration(milliseconds: 450);
  static const Duration _routeFadeDuration = Duration(milliseconds: 280);

  @override
  void initState() {
    super.initState();

    _controller = VideoPlayerController.asset('assets/home_screen.mp4');
    _initVideo();
  }

  Future<void> _initVideo() async {
    await _controller.initialize();
    if (!mounted) return;

    _controller.play();

    // fade-out
    _fadeTimer = Timer(const Duration(seconds: 8), () {
      if (!mounted || _isFinished) return;
      setState(() => _opacity = 0);
    });

    _continueTimer = Timer(const Duration(milliseconds: 8500), () {
      if (!mounted || _isFinished) return;
      _finishAndGoNext();
    });

    setState(() {});
  }

  void _skip() {
    if (_isFinished) return;
    _isFinished = true;

    _fadeTimer?.cancel();
    _continueTimer?.cancel();

    if (_controller.value.isInitialized) {
      _controller.pause();
    }

    _finishAndGoNext();
  }

  void _finishAndGoNext() {
    if (_isNavigating) return;
    _isNavigating = true;

    setState(() => _opacity = 0);

    Future.delayed(_fadeDuration, () {
      if (!mounted) return;

      Navigator.of(context).pushReplacement(_fadeRoute(const OnboardingGate()));
    });
  }

  PageRouteBuilder<void> _fadeRoute(Widget page) {
    return PageRouteBuilder<void>(
      transitionDuration: _routeFadeDuration,
      reverseTransitionDuration: _routeFadeDuration,
      pageBuilder: (_, __, ___) => page,
      transitionsBuilder: (_, animation, __, child) {
        return FadeTransition(
          opacity: CurvedAnimation(parent: animation, curve: Curves.easeInOut),
          child: child,
        );
      },
    );
  }

  @override
  void dispose() {
    _fadeTimer?.cancel();
    _continueTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _controller.value.isInitialized
          ? Stack(
              fit: StackFit.expand,
              children: [
                AnimatedOpacity(
                  opacity: _opacity,
                  duration: _fadeDuration,
                  curve: Curves.easeInOut,
                  child: FittedBox(
                    fit: BoxFit.cover,
                    child: SizedBox(
                      width: _controller.value.size.width,
                      height: _controller.value.size.height,
                      child: VideoPlayer(_controller),
                    ),
                  ),
                ),

                // button for skipping splash screen
                SafeArea(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Align(
                      alignment: Alignment.topRight,
                      child: AnimatedOpacity(
                        opacity: _opacity,
                        duration: _fadeDuration,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: Colors.black.withOpacity(0.35),
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: TextButton(
                            onPressed: _skip,
                            child: const Text(
                              'Skip intro',
                              style: TextStyle(color: Colors.white),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            )
          : const Center(child: CircularProgressIndicator()),
    );
  }
}
