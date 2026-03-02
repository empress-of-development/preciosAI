import 'package:flutter/material.dart';
import 'package:preciosai/introduction/animated_intro_screen_1.dart';
import 'package:preciosai/introduction/animated_intro_screen_2.dart';
import 'package:preciosai/introduction/animated_intro_screen_3.dart';
import 'package:preciosai/introduction/animated_intro_screen_4.dart';
import 'package:preciosai/introduction/animated_intro_screen_5.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key, required this.onDone});

  final VoidCallback onDone;

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  late final PageController _controller;
  int _index = 0;

  int get _pageCount => 5;

  @override
  void initState() {
    super.initState();
    _controller = PageController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _next() {
    if (_index < _pageCount - 1) {
      _controller.nextPage(
        duration: const Duration(milliseconds: 420),
        curve: Curves.easeOutCubic,
      );
    } else {
      widget.onDone();
    }
  }

  @override
  Widget build(BuildContext context) {
    final isLast = _index == _pageCount - 1;

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(
          children: [
            PageView(
              controller: _controller,
              physics: const BouncingScrollPhysics(),
              onPageChanged: (i) => setState(() => _index = i),
              children: [
                AnimatedIntroScreen1(
                  beforeModelAsset: 'assets/onboarding/screen_1/before.png',
                  afterModelAsset: 'assets/onboarding/screen_1/after.png',
                  isActive: _index == 0,
                ),
                AnimatedIntroScreen2(
                  gridAsset: 'assets/onboarding/screen_2/grid.png',
                  isActive: _index == 1,
                ),
                AnimatedIntroScreen3(
                  centerAsset: 'assets/onboarding/screen_3/screen_3.png',
                  isActive: _index == 2,
                ),
                AnimatedIntroScreen4(
                  centerAsset: 'assets/onboarding/screen_4/screen_4.png',
                  isActive: _index == 3,
                ),
                AnimatedIntroScreen5(
                  centerAsset: 'assets/onboarding/screen_5/screen_5.png',
                  isActive: _index == 4,
                ),
              ],
            ),

            // Нижняя панель управления
            Positioned(
              left: 16,
              right: 16,
              bottom: 16,
              child: _BottomBar(
                count: _pageCount,
                index: _index,
                isLast: isLast,
                onSkip: widget.onDone,
                onNext: _next,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BottomBar extends StatelessWidget {
  const _BottomBar({
    required this.count,
    required this.index,
    required this.isLast,
    required this.onSkip,
    required this.onNext,
  });

  final int count;
  final int index;
  final bool isLast;
  final VoidCallback onSkip;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        TextButton(
          onPressed: onSkip,
          style: TextButton.styleFrom(foregroundColor: Colors.white70),
          child: const Text('Skip'),
        ),
        const Spacer(),
        _Dots(count: count, index: index),
        const Spacer(),
        FilledButton(
          onPressed: onNext,
          style: FilledButton.styleFrom(
            backgroundColor: const Color(0xFFFF6FB1),
            foregroundColor: Colors.black,
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
            ),
          ),
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 180),
            transitionBuilder: (child, anim) =>
                ScaleTransition(scale: anim, child: child),
            child: Text(isLast ? 'Done' : 'Next', key: ValueKey(isLast)),
          ),
        ),
      ],
    );
  }
}

class _Dots extends StatelessWidget {
  const _Dots({required this.count, required this.index});

  final int count;
  final int index;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: List.generate(count, (i) {
        final selected = i == index;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
          margin: const EdgeInsets.symmetric(horizontal: 4),
          width: selected ? 18 : 8,
          height: 8,
          decoration: BoxDecoration(
            color: selected ? Colors.white : Colors.white38,
            borderRadius: BorderRadius.circular(999),
          ),
        );
      }),
    );
  }
}
