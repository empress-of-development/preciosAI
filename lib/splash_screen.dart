import 'dart:async';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import 'package:preciosai/onboarding_gate.dart';

class SplashScreenVideo extends StatefulWidget {
  const SplashScreenVideo({super.key});

  @override
  State<SplashScreenVideo> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreenVideo> {
  late VideoPlayerController _controller;
  double _opacity = 1.0;

  @override
  void initState() {
    super.initState();

    _controller = VideoPlayerController.asset('assets/home_screen.mp4')
      ..initialize().then((_) {
        //_controller.setLooping(true);
        //_controller.setVolume(0); // без звука
        _controller.play();

        // fade-out
        Future.delayed(const Duration(seconds: 8), () {
          if (!mounted) return;

          setState(() => _opacity = 0);
        });

        Future.delayed(const Duration(milliseconds: 8500), () {
          if (mounted) {
            _continue();
          }
        });

        setState(() {});
      });
  }

  void _continue() {
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const OnboardingGate()),
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
      body: _controller.value.isInitialized
          ? AnimatedOpacity(
              opacity: _opacity,
              duration: const Duration(milliseconds: 500),
              curve: Curves.easeOut,
              child: SizedBox.expand(
                child: FittedBox(
                  fit: BoxFit.cover,
                  child: SizedBox(
                    width: _controller.value.size.width,
                    height: _controller.value.size.height,
                    child: VideoPlayer(_controller),
                  ),
                ),
              ),
            )
          : const Center(child: CircularProgressIndicator()),
    );
  }
}
