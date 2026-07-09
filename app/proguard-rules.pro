# kotlinx.serialization keeps generated serializers; keep the @Serializable metadata.
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class dev.mtib.squadventure.**$$serializer { *; }
-keepclassmembers class dev.mtib.squadventure.** {
    *** Companion;
}
-keepclasseswithmembers class dev.mtib.squadventure.** {
    kotlinx.serialization.KSerializer serializer(...);
}
