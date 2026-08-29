# Add project specific ProGuard rules here.

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Data models
-keep class com.jiotvplus.app.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt
-dontwarn dagger.hilt.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Media3
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Keep TokenStore methods (used by Frida)
-keep class com.jiotvplus.app.data.prefs.TokenStore { *; }

# Keep BuildConfig
-keep class com.jiotvplus.app.BuildConfig { *; }

# Kotlin metadata
-keepattributes KotlinAnnotation, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
