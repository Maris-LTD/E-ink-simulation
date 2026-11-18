-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class org.readium.** { *; }
-keep interface org.readium.** { *; }
-dontwarn org.readium.**

-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**

-keep class com.shockwave.pdfium.** { *; }
-keep class io.legere.pdfiumandroid.** { *; }
-dontwarn com.shockwave.pdfium.**
-dontwarn io.legere.pdfiumandroid.**

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers @kotlinx.serialization.Serializable class com.example.emptyapp.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.example.emptyapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.emptyapp.**$$serializer { *; }
