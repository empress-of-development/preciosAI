# Flutter
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# MediaPipe — полное сохранение
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-keep enum com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-keepclasseswithmembers class com.google.mediapipe.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn com.google.mediapipe.**

# Предотвращает проблемы со статическими инициализаторами
-keepclassmembers class * {
    static <fields>;
    static <methods>;
}

# Protobuf (используется внутри MediaPipe)
-keep class com.google.protobuf.** { *; }
-keepclassmembers class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**