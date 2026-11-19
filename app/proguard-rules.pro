# RailX release rules.
# Keep the app's own classes untouched by shrinking/obfuscation: the
# release build still optimizes the libraries (Compose, Material,
# networking), which is where the runtime cost lives, while Room
# entities, payment and JSON handling in app code stay exactly as
# tested in debug builds.
-keep class com.kaushik.railway.** { *; }
-keepclassmembers class com.kaushik.railway.** { *; }
