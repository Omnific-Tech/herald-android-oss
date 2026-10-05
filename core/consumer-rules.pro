# Retain generated serializers when a consumer minifies its APK.
-keepclassmembers class dev.omnific.herald.** {
    *** Companion;
}
-keepclasseswithmembers class dev.omnific.herald.** {
    kotlinx.serialization.KSerializer serializer(...);
}
