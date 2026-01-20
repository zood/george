# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/arash/apps/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Don't minify any zood code
-keep class io.pijun.george.** { *; }
-keep class xyz.zood.george.** { *; }

# Inner class protection
-keep class io.pijun.george.*$* { *; }
-keep class xyz.zood.george.*$* { *; }

-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector
-dontwarn com.google.firebase.ktx.Firebase

# OkHttp
-dontwarn com.squareup.okhttp.Cache
-dontwarn com.squareup.okhttp.CacheControl$Builder
-dontwarn com.squareup.okhttp.CacheControl
-dontwarn com.squareup.okhttp.Call
-dontwarn com.squareup.okhttp.OkHttpClient
-dontwarn com.squareup.okhttp.Request$Builder
-dontwarn com.squareup.okhttp.Request
-dontwarn com.squareup.okhttp.Response
-dontwarn com.squareup.okhttp.ResponseBody

# retain source file name and line numbers
-keepattributes SourceFile,LineNumberTable
