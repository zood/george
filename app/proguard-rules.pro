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

# okio (pulled in from retrofit2)
-dontwarn okio.**
# retrofit2
-dontwarn retrofit2.Platform$Java8

# Don't minify any zood code
-keep class io.pijun.** {
    public protected private *;
}
-keep class xyz.zood.** {
    public protected private *;
}
# Inner class protection
-keep class io.pijun.*$* {
    *;
}
-keep class xyz.zood.*$* {
    *;
}

# For Android-Image-Croper library
-keep class android.support.v7.widget.** { *; }