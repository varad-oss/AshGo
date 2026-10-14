# AshGo release shrinking (Stage 11). Firebase RTDB is only ever given maps and primitives (no POJO reflection),
# kotlinx.serialization and AndroidX ship their own consumer rules, and manifest components are kept by AGP.

# Readable crash traces without shipping source names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# osmdroid reads tile sources and config reflectively in places.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
