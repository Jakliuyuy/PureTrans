# ProGuard rules for PureTrans
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep data models and serialization classes
-keep class com.puretrans.app.data.model.** { *; }
-keep class com.puretrans.app.data.engine.**$* { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep OkHttp & Gson
-keepclassmembers enum * { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
