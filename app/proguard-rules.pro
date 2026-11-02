# ML Kit (OCR incluido): conservar clases usadas por reflexión/JNI y los protos internos.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-keep class com.google.firebase.components.** { *; }
-dontwarn com.google.**
