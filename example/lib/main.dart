import 'package:flutter/material.dart';
import 'package:preciosai/splash_screen.dart';
import 'package:google_fonts/google_fonts.dart';

void main() {
  runApp(const PreciosAIDemo());
}

class PreciosAIDemo extends StatelessWidget {
  const PreciosAIDemo({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PreciosAI Demo',
      theme: ThemeData(
        scaffoldBackgroundColor: const Color(0xFFF6F7F9),
        textTheme: GoogleFonts.didactGothicTextTheme(),
        colorScheme: const ColorScheme.light(
          primary: Color(0xFF9EE6DF),
          secondary: Color(0xFFBFC8D8),
        ),
        useMaterial3: true,
      ),
      home: const SplashScreenVideo(),
    );
  }
}
