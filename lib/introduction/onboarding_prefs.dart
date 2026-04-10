import 'package:shared_preferences/shared_preferences.dart';

class OnboardingPrefs {
  static const _key = 'onboarding_done';

  static Future<bool> isDone() async {
    // final prefs = await SharedPreferences.getInstance();
    // return prefs.getBool(_key) ?? false;
    return true;
  }

  static Future<void> markDone() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_key, true);
  }

  static Future<void> reset() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
