# ============================================================
# ClimaAI ProGuard Rules
# Play Store Security: Obfuscation and shrinking
# ============================================================

# ---- General Rules ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ---- Retrofit ----
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# R8 full mode (the default from AGP 8) strips generic signatures from types
# that are not explicitly kept. Retrofit reads Response<T> / Call<T> at runtime
# to build its call adapters, so without these the release build fails every
# request with:
#
#   Response must include generic type (e.g., Response<String>)
#   for method ClimaAIApi.getWeather
#
# This only affects minified builds, so debug is unaffected and it ships broken
# unless the release variant is actually run. The Continuation keep below was
# already here — these two are the rest of the same official rule set.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- Gson ----
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ---- Data Classes (API Models) ----
-keep class com.climaai.app.data.** { *; }
-keep class com.climaai.app.data.api.** { *; }

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- Google Play Billing ----
-keep class com.android.vending.billing.** { *; }

# ---- Glance Widgets ----
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Coroutines ----
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---- Security: Never keep debug info ----
-renamesourcefileattribute SourceFile
