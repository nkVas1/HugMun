# HugMun R8 rules.
#
# The app has no server and no reflection-heavy frameworks, so the rule set is small
# by design. Anything added here needs a comment explaining why R8 cannot work it out.

# kotlinx.serialization: keep the generated serializers for @Serializable types.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.hugmun.**$$serializer { *; }
-keepclasseswithmembers class com.hugmun.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room 3 generated implementations are referenced by name at runtime.
-keep class com.hugmun.**_Impl { *; }

# Keep line numbers for readable crash reports in a local-only app.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
