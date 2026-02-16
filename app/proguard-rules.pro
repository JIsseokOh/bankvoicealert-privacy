# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep TTS related classes
-keep class android.speech.tts.** { *; }

# Keep notification listener service
-keep class com.family.bankvoicealert.BankNotificationService { *; }

# Keep SMS receiver
-keep class com.family.bankvoicealert.SmsReceiver { *; }

# Keep foreground service
-keep class com.family.bankvoicealert.ForegroundService { *; }

# Keep main activity
-keep class com.family.bankvoicealert.MainActivity { *; }

# Keep TTS Manager
-keep class com.family.bankvoicealert.TTSManager { *; }

# Keep parser classes
-keep class com.family.bankvoicealert.BankParser { *; }
-keep class com.family.bankvoicealert.SmsParser { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions

# Keep generic type information for Kotlin
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep all classes in our package
-keep class com.family.bankvoicealert.** { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }

# Google Billing
-keep class com.android.billingclient.** { *; }
-keep class com.android.vending.billing.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep DataBinding
-keep class * extends androidx.databinding.ViewDataBinding { *; }

# Prevent R8 from removing important methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}