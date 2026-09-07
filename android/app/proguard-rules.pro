# Supabase & Ktor Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn io.ktor.**
-keep class io.github.jan.supabase.** { *; }
-keep class com.documentsync.app.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
