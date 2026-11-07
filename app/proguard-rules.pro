# GeckoView 159 consumer rules from Mozilla's 7b34a03a910d0385299f4a9f27aa585a786862d8 source revision.
# Source: https://hg.mozilla.org/releases/mozilla-release/raw-file/7b34a03a910d0385299f4a9f27aa585a786862d8/mobile/android/geckoview/proguard-rules.txt

# Preserve annotations, native method names, and Android framework entry points.
-keepattributes *Annotation*

-keepclassmembernames class * {
    java.lang.Class class$(java.lang.String);
    java.lang.Class class$(java.lang.String, boolean);
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclassmembers class * implements android.os.Parcelable {
    static android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
  public static <fields>;
}

# GeckoView Java APIs and native/reflection targets.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.SysInfo { *; }
-keep class org.mozilla.gecko.mozglue.JNIObject { *; }
-keep class * extends org.mozilla.gecko.mozglue.JNIObject { *; }

-keep @interface org.mozilla.gecko.annotation.JNITarget
-keep @org.mozilla.gecko.annotation.JNITarget class *
-keepclassmembers @org.mozilla.gecko.annotation.JNITarget class * { *; }
-keepclassmembers class * { @org.mozilla.gecko.annotation.JNITarget *; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.JNITarget <methods>; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.JNITarget <fields>; }

-keep @interface org.mozilla.gecko.annotation.WebRTCJNITarget
-keep @org.mozilla.gecko.annotation.WebRTCJNITarget class *
-keepclassmembers class * { @org.mozilla.gecko.annotation.WebRTCJNITarget *; }
-keepclassmembers @org.mozilla.gecko.annotation.WebRTCJNITarget class * { *; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.WebRTCJNITarget <methods>; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.WebRTCJNITarget <fields>; }

-keep @interface org.mozilla.gecko.annotation.WrapForJNI
-keep @org.mozilla.gecko.annotation.WrapForJNI class *
-keepclassmembers,includedescriptorclasses class * {
    @org.mozilla.gecko.annotation.WrapForJNI *;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.mozilla.gecko.annotation.WrapForJNI <methods>;
}
-keepclasseswithmembers,includedescriptorclasses class * {
    @org.mozilla.gecko.annotation.WrapForJNI <fields>;
}
-keepclassmembers,includedescriptorclasses @org.mozilla.gecko.annotation.WrapForJNI class * {
    *;
}

-keep @interface org.mozilla.gecko.annotation.ReflectionTarget
-keep @org.mozilla.gecko.annotation.ReflectionTarget class *
-keepclassmembers class * { @org.mozilla.gecko.annotation.ReflectionTarget *; }
-keepclassmembers @org.mozilla.gecko.annotation.ReflectionTarget class * { *; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.ReflectionTarget <methods>; }
-keepclasseswithmembers class * { @org.mozilla.gecko.annotation.ReflectionTarget <fields>; }

-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Optional SMBJ/Mbassy paths reference Java SE APIs not present on Android. SMB
# NTLM and normal event dispatch do not require these EL/Kerberos classes.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
