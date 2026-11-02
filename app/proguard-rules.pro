# JNI entry points keep their names for the Android PRoot process bridge.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
