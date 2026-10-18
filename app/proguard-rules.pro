# Tag Forge R8 rules (RB-01). Room, Navigation, Compose, DataStore and kotlinx.serialization ship consumer rules; the
# lines below only guarantee what our own @Serializable classes (navigation routes, RecordSpec, tasks, export DTOs) need.
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# kotlinx.serialization: the generated serializers and the Companion.serializer() lookups used by type-safe Navigation.
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tagforge.**$$serializer { *; }
-keepclassmembers class com.tagforge.** { *** Companion; }
-keepclasseswithmembers class com.tagforge.** { kotlinx.serialization.KSerializer serializer(...); }

# Persisted enums use explicit codes, never name(), so renaming them is safe. Nothing else is kept on purpose.
