# JNI entry points keep their names for the Android PRoot process bridge.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# zstd-jni resolves Java fields (including srcPos/dstPos) and classes by name
# from native code. Keeping native method names alone does not protect them.
# Preserve the complete JNI boundary, including fields only used by C code.
-keep class com.github.luben.zstd.** { *; }
