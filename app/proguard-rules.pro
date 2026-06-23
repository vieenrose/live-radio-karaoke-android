# sherpa-onnx & llama bridge are reached via JNI reflection — keep their members.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class io.github.vieenrose.liveradiokaraoke.llm.LlamaBridge { *; }

# opencc4j loads dictionaries reflectively.
-keep class com.github.houbb.opencc4j.** { *; }
-dontwarn com.github.houbb.**

# Media3 / OkHttp ship their own consumer rules; silence platform-only warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
