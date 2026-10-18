# Release builds are shrunk by default (-PminifyRelease=false turns it off).

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization: keep generated serializers and the classes they describe.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.forgequest.app.**$$serializer { *; }
-keepclassmembers class com.forgequest.app.** { *** Companion; }
-keepclasseswithmembers class com.forgequest.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Room entities and the Gemini / Drive JSON models are read by name.
-keep class com.forgequest.app.data.db.** { *; }
-keep class com.forgequest.app.data.backup.** { *; }
-keep class com.forgequest.app.data.plan.** { *; }
-keep class com.forgequest.app.data.FoodScan { *; }
-keep class com.forgequest.app.data.ScanItem { *; }

# Health Connect records are created reflectively when reading by type.
-keep class androidx.health.connect.client.records.** { *; }
# Tink / security-crypto used for the encrypted API keys.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
