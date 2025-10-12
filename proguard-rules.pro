# ============ Disable Obfuscation ============
-dontoptimize
-dontobfuscate
-dontpreverify

# ============ Basic Attributes ============
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations

# ============ Suppress Warnings ============
-dontwarn io.micrometer.context.**
-dontwarn javax.enterprise.inject.spi.**
-dontwarn okhttp3.internal.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**
-dontwarn io.netty.util.internal.logging.**
-dontwarn io.lettuce.core.support.**
-dontwarn java.lang.management.**

# ============ Kotlinx Serialization ============
# Keep @Serializable classes structure
-keep @kotlinx.serialization.Serializable class ** {
    <fields>;
    <init>(...);
}

# Keep serialization metadata
-keepclassmembers class ** {
    *** Companion;
}

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class project.pipepipe.** { *; }

# ============ Enum Support ============
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============ Native Methods ============
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============ R8 Optimization ============
-allowaccessmodification

# ============ Debugging (Optional) ============
# -printconfiguration build/outputs/mapping/configuration.txt
# -printusage build/outputs/mapping/usage.txt
