# kotlinx.serialization keeps the generated serializers off the entry graph.
-keepclassmembers class dev.omnific.herald.** {
    *** Companion;
}
-keepclasseswithmembers class dev.omnific.herald.** {
    kotlinx.serialization.KSerializer serializer(...);
}
