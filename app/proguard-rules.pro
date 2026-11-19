-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses 'o'
-allowaccessmodification

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static int wtf(...);
}

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
