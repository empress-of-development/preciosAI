import 'package:flutter/foundation.dart';

enum LogLevel { debug, info, warning, error }

class Logger {
  const Logger._();

  static const _tag = '[YOLO]';

  static void debug(String message) {
    _log(LogLevel.debug, message);
  }

  static void info(String message) {
    _log(LogLevel.info, message);
  }

  static void warning(String message) {
    _log(LogLevel.warning, message);
  }

  static void error(String message, {Object? error, StackTrace? stackTrace}) {
    _log(LogLevel.error, message, error: error, stackTrace: stackTrace);
  }

  static void _log(
    LogLevel level,
    String message, {
    Object? error,
    StackTrace? stackTrace,
  }) {
    if (kReleaseMode && level == LogLevel.debug) return;

    final time = DateTime.now().toIso8601String();
    final levelStr = level.name.toUpperCase();

    final buffer = StringBuffer()
      ..write('$_tag [$levelStr] $time ')
      ..write(message);

    if (error != null) {
      buffer.write('\n  error: $error');
    }

    if (stackTrace != null) {
      buffer.write('\n  stack:\n$stackTrace');
    }

    debugPrint(buffer.toString());
  }
}
