# jPill release shrinker rules. Room, Hilt, Compose, Navigation, Glance and AndroidX ship their own consumer rules.

# Readable crash reports: keep file names and line numbers.
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Enums are stored and looked up by name (Room converters, SharedPreferences): never rename or drop their constants.
-keepclassmembers enum com.jagger.timer.** { *; }
