# kotlinx.serialization — keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.karelisio.somna.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.karelisio.somna.** {
    kotlinx.serialization.KSerializer serializer(...);
}
