# Argon2Kt memakai JNI
-keep class com.lambdapioneer.argon2kt.** { *; }

# BouncyCastle (hanya SHA3Digest yang dipakai langsung)
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.crypto.digests.SHA3Digest { *; }

# OkHttp: platform opsional
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Tink (dipakai androidx.security:security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
