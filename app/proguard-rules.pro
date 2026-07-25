# Vera — R8/ProGuard rules. Release build keeps minify off for now (see build.gradle.kts).
# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class app.vera.**$$serializer { *; }
-keepclassmembers class app.vera.** {
    *** Companion;
}
-keepclasseswithmembers class app.vera.** {
    kotlinx.serialization.KSerializer serializer(...);
}
