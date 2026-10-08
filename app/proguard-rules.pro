# Keep all JNI native methods across the application
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI model classes used by native C/Rust JNI engines
-keep class com.crestcode.core.filesystem.LocalFileSystem** { *; }
-keep class com.crestcode.core.model.** { *; }
-keep class com.crestcode.runtime.** { *; }
-keep class com.termux.** { *; }
-keep class com.jcraft.jsch.** { *; }

# Keep Compose metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
