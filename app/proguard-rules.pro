# ==============================================================================
# USBAnywhere Android — Production ProGuard & R8 Hardening Rules
# ==============================================================================

# Line numbers and source files for clean crash traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve standard annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# Kotlin Coroutines & Flow
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ------------------------------------------------------------------------------
# Kotlinx Serialization
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------------------
# USB/IP Binary Protocol, DTOs & Descriptors
# (Must preserve binary field layout for Big-Endian network serialization)
# ------------------------------------------------------------------------------
-keep class com.usbanywhere.protocol.** { *; }
-keep class com.usbanywhere.data.** { *; }
-keep class com.usbanywhere.catalog.** { *; }
-keep class com.usbanywhere.net.wan.** { *; }
-keep class com.usbanywhere.audio.** { *; }
-keep class com.usbanywhere.gamepad.** { *; }
-keep class com.usbanywhere.gaming.** { *; }

# ------------------------------------------------------------------------------
# Android System Components & Reflection Boundaries
# ------------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Ensure Foreground Service and Broadcast Receivers remain intact
-keep class com.usbanywhere.server.UsbIpServerService { *; }
-keep class com.usbanywhere.receiver.BootCompletedReceiver { *; }
-keep class com.usbanywhere.receiver.UsbAttachedReceiver { *; }

# ------------------------------------------------------------------------------
# Compose & Material 3
# ------------------------------------------------------------------------------
-dontwarn androidx.compose.**
-keep class androidx.compose.ui.platform.** { *; }
