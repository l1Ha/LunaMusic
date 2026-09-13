# Keep Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.luna.music.**$$serializer { *; }
-keepclassmembers class com.luna.music.** { *** Companion; }
-keepclasseswithmembers class com.luna.music.** { kotlinx.serialization.KSerializer serializer(...); }
