# Chaquopy rules
-keep class com.chaquo.python.** { *; }
-keep class com.chaquo.stdrc.** { *; }

# Room rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Ktor rules
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn java.lang.management.**

# SLF4J rules
-dontwarn org.slf4j.**

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepnames class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Rhino rules
-dontwarn java.beans.**
-dontwarn javax.script.**

# NewPipe Extractor & Nanojson rules
-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-keep class com.grack.nanojson.** { *; }
-dontwarn com.grack.nanojson.**
