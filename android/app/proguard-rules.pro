# Crest Editor Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep JavascriptInterfaces for WebView Bridge
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Compose reflection
-keep class androidx.compose.** { *; }
