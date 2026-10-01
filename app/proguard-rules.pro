# POC Phase 1 — Minimal ProGuard rules
# No minification needed for POC, but keep these for safety

# Keep data classes used by SnakeYAML
-keep class com.proj.automation.parser.** { *; }
-keep class com.proj.automation.engine.** { *; }
-keep class com.proj.automation.selector.** { *; }

# Keep composable functions
-keep class * extends androidx.compose.runtime.Composer

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.** { *; }
-keepclassmembers class kotlinx.coroutines.CoroutineScope { *; }
