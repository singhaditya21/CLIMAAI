# ============================================================
# ClimaAI Wear ProGuard Rules
#
# The release build already named this file in build.gradle but it was never
# written. It is needed now that the watch fetches its own weather: R8 in full
# mode (the AGP 8 default) strips the generic signatures Retrofit reads and
# renames the fields Gson matches by name, so a minified watch APK would parse
# every response into nulls and report "No data" forever — while debug builds
# worked fine.
# ============================================================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---- Retrofit ----
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# Retrofit inspects Response<T> at runtime to pick a call adapter; without these
# every request fails with "Response must include generic type".
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Gson ----
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Watch data models ----
# Both the Open-Meteo responses and the cached reading are read back by field
# name, the cache across app versions.
-keep class com.climaai.wear.data.** { *; }

# ---- Coroutines ----
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
