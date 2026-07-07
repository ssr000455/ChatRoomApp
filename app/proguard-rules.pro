# Keep Retrofit and Gson models
-keepattributes Signature
-keepattributes *Annotation*

# Keep data models for Gson serialization
-keep class com.chatroom.app.data.model.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.chatroom.app.data.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
