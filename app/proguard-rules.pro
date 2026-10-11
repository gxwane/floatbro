# FloatBro Production ProGuard / R8 Rules

# 1. Preserve JNI native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Preserve C++ AdBlockClient and JNI bridge classes
-keep class io.github.floatbro.adblock.** { *; }

# 3. Preserve WebView JavaScript Interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 4. Keep stack trace line numbers and source file names for crash analysis
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 5. Preserve annotations, generics signatures, and inner classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 6. Logging / Timber warnings suppression
-dontwarn org.slf4j.**
