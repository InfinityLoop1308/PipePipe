-dontwarn io.micrometer.context.**
-dontwarn javax.enterprise.inject.spi.**
-dontwarn okhttp3.internal.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.**
-dontwarn io.netty.util.internal.logging.**
-dontwarn io.lettuce.core.support.**
-dontwarn java.lang.management.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# Kotlinx Serialization
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializers
-keep,includedescriptorclasses class project.pipepipe.**$$serializer { *; }
-keepclassmembers class project.pipepipe.** {
    *** Companion;
}
-keepclasseswithmembers class project.pipepipe.** {
    kotlinx.serialization.KSerializer serializer(...);
}