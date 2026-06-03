# Keep Chaquopy classes
-keep class com.chaquo.python.** { *; }
-keep class com.nszconverter.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
