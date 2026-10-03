// Lets `core` be built on its own, e.g. included by the plugins repository
// (github.com/onova-tech/android-automation-plugins) to build the agp tool with the same engine
// the phone runs. Inside this repository, core is an ordinary subproject and this file is ignored.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.22"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "core"
