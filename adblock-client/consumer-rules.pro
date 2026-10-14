# Preserve C++ JNI bridge classes and native method signatures
-keep class io.github.edsuns.adblockclient.** { *; }

# keep <init> function, which will be called by native lib
-keepclassmembers class io.github.edsuns.adblockclient.MatchResult { <init>(boolean,java.lang.String,java.lang.String); }
