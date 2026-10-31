// Top-level build file — defines plugin versions only
// Repositories are managed in settings.gradle.kts (FAIL_ON_PROJECT_REPOS)
plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
