# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Timber
-dontwarn org.jetbrains.annotations.**

# Eclipse Paho MQTT
-dontwarn org.eclipse.paho.client.mqttv3.**
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-keep class org.eclipse.paho.client.mqttv3.internal.** { *; }

# 防止 R8 内联敏感常量（NAS_BASE_URL 等）
-keepclassmembers class com.example.thoughtvault.data.local.SettingsDataStore {
    static final java.lang.String NAS_BASE_URL;
    static final java.lang.String KEY_PASSWORD;
}
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi