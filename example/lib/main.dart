import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:preciosai/l10n/app_localizations.dart';
import 'package:preciosai/splash_screen.dart';
import 'package:showcaseview/showcaseview.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Это заставляет приложение отрисовываться под системными панелями
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  ShowcaseView.register();

  runApp(const PreciosAIDemo());
}

class PreciosAIDemo extends StatelessWidget {
  const PreciosAIDemo({super.key});

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: const SystemUiOverlayStyle(
        systemNavigationBarColor: Colors.transparent,
        systemNavigationBarDividerColor: Colors.transparent,
        systemNavigationBarIconBrightness: Brightness.light,
        systemNavigationBarContrastEnforced: false,
      ),
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'PreciosAI Demo',
        localizationsDelegates: const [
          AppLocalizations.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: AppLocalizations.supportedLocales,
        localeResolutionCallback: (locale, supportedLocales) {
          for (var supportedLocale in supportedLocales) {
            if (supportedLocale.languageCode == locale?.languageCode) {
              return supportedLocale;
            }
          }
          return const Locale('en');
        },
        theme: ThemeData(
          scaffoldBackgroundColor: const Color(0xFFF6F7F9),
          textTheme: GoogleFonts.didactGothicTextTheme(),
          colorScheme: const ColorScheme.light(
            primary: Colors.indigo,
            secondary: Color(0xFFBFC8D8),
          ),
          useMaterial3: true,
        ),
        home: const SplashScreenVideo(),
      ),
    );
  }
}
