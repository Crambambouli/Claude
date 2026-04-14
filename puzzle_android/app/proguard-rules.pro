# Keep Retrofit interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Gson models
-keep class com.puzzle.android.data.model.** { *; }
-keepclassmembers class com.puzzle.android.data.model.** { *; }

# Keep Room entities and DAOs
-keep class com.puzzle.android.data.db.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
