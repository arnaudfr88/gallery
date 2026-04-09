# ========================
# Basics (recommended)
# ========================

# Keep annotations (many libraries depend on them).
-keepattributes *Annotation*

# Keep generic signatures (serialization / reflection).
-keepattributes Signature

# Keep inner class metadata.
-keepattributes InnerClasses

# for Gson
-keep class com.google.ai.edge.gallery.ui.home.ReleaseInfo { *; }
-keep class com.google.ai.edge.gallery.data.** { *; }

# Kotlin metadata (important for reflective access).
-keep class kotlin.Metadata { *; }

# ========================
# Kotlin / Coroutines
# ========================

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ========================
# kotlinx.serialization
# ========================
# Used for API DTOs (e.g. Ktor JSON) — see OpenAIRequest / OpenAIResponse.

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**

# ========================
# Ktor Server
# ========================
# Matches ktor-server-* dependencies in app/build.gradle.kts.

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ========================
# Moshi (Kotlin codegen)
# ========================
# @JsonClass(generateAdapter = true) in this app; keep generated *JsonAdapter.

-if class * {
    @com.squareup.moshi.JsonClass *;
}
-keep class *JsonAdapter {
    *;
}

# ========================
# Reflection (generic fallback)
# ========================

-keep class * {
    @kotlin.Metadata *;
}

# ========================
# Protobuf
# ========================
-keep class com.google.ai.edge.gallery.proto.** { *; }

# ========================
# Common warnings
# ========================

-dontwarn org.jetbrains.annotations.**

# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.api.client.http.GenericUrl
-dontwarn com.google.api.client.http.HttpHeaders
-dontwarn com.google.api.client.http.HttpRequest
-dontwarn com.google.api.client.http.HttpRequestFactory
-dontwarn com.google.api.client.http.HttpResponse
-dontwarn com.google.api.client.http.HttpTransport
-dontwarn com.google.api.client.http.javanet.NetHttpTransport$Builder
-dontwarn com.google.api.client.http.javanet.NetHttpTransport
-dontwarn org.joda.time.Instant
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
