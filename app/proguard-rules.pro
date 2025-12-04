# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep Lumina data classes for JSON serialization
-keep class com.lumina.engine.ColorRGBA { *; }
-keep class com.lumina.engine.Vec2 { *; }
-keep class com.lumina.engine.Vec3 { *; }
-keep class com.lumina.engine.EffectParams { *; }
-keep class com.lumina.engine.CameraState { *; }
-keep class com.lumina.engine.GlassmorphicParams { *; }
-keep class com.lumina.engine.AIIntent { *; }
-keep class com.lumina.engine.FrameTiming { *; }
-keep class com.lumina.engine.LuminaState { *; }

# Keep enums
-keepclassmembers enum com.lumina.engine.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Chaquopy Python
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Jetpack Compose
-dontwarn androidx.compose.**
