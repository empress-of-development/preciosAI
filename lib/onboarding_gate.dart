import 'package:flutter/material.dart';
import 'package:preciosai/album_page.dart';
import 'package:preciosai/introduction/onboarding_prefs.dart';
import 'package:preciosai/introduction/onboarding_screen.dart';

class OnboardingGate extends StatelessWidget {
  const OnboardingGate({super.key});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<bool>(
      future: OnboardingPrefs.isDone(),
      builder: (context, snap) {
        // лоадер пока prefs читаются
        if (snap.connectionState != ConnectionState.done) {
          return const _Splash();
        }

        final done = snap.data ?? false;
        if (done) return const AlbumScreen();

        return OnboardingScreen(
          onDone: () async {
            await OnboardingPrefs.markDone();
            if (!context.mounted) return;

            Navigator.of(context).pushReplacement(
              MaterialPageRoute(builder: (_) => const AlbumScreen()),
            );
          },
        );
      },
    );
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: Colors.black,
      body: Center(child: CircularProgressIndicator()),
    );
  }
}
