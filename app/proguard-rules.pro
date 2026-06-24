# sherpa-onnx & llama bridge are reached via JNI reflection — keep their members.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class io.github.vieenrose.liveradiokaraoke.llm.LlamaBridge { *; }

# opencc4j loads dictionaries reflectively.
-keep class com.github.houbb.opencc4j.** { *; }
-dontwarn com.github.houbb.**

# commons-compress (BZip2 + Tar) extracts the X-ASR model; keep it + silence optional-codec warnings.
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.**
-dontwarn org.tukaani.**
-dontwarn com.github.luben.**

# Media3 / OkHttp ship their own consumer rules; silence platform-only warnings.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
