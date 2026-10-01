# Android Project Scaffold Pattern

## Purpose

Standardize Android project setup with Gradle (Kotlin DSL), AGP 8.x, Kotlin 1.9+, and the required configuration for an Accessibility Service-based automation runtime.

## When This Pattern Applies

Use this pattern when:
- Setting up a new Android project from scratch
- Configuring Gradle build files for Android automation
- Establishing minimum SDK, target SDK, and required permissions
- Adding the Automation Service declaration to the manifest
- Setting up project structure with editor/, parser/, engine/, selector/, service/ packages

## Configuration

### Gradle — Project-level `build.gradle.kts`

```kotlin
// Top-level build file — defines plugin versions and repositories
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
```

### Gradle — App-level `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.proj.automation"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.proj.automation"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ——— YAML Parsing ———
    implementation("org.yaml:snakeyaml:2.2")

    // ——— Jetpack Compose ———
    val composeBom = platform("androidx.compose:compose-bom:2024.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ——— Core Android ———
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // ——— Coroutines ———
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ——— Testing ———
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
```

### Gradle — `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-automation-app-poc"
include(":app")
```

### Gradle — `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

## AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required: Foreground service notification -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- Optional: Prevent OEM battery optimization from killing service -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".App"
        android:allowBackup="false"
        android:label="Android Automation"
        android:supportsRtl="true"
        android:theme="@style/Theme.AndroidAutomation">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.AndroidAutomation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Automation Service — BIND_ACCESSIBILITY_SERVICE is system-level,
             granted only when user enables the service in system settings -->
        <service
            android:name=".service.AutomationService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false"
            android:foregroundServiceType="dataSync">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

## Service Configuration — `res/xml/accessibility_service_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:settingsActivity="com.proj.automation.MainActivity" />
```

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/proj/automation/
│   ├── App.kt                          — Application class
│   ├── MainActivity.kt                 — Compose UI host
│   ├── editor/
│   │   └── WorkflowEditorScreen.kt     — YAML editor Compose screen
│   ├── parser/
│   │   ├── YamlParser.kt               — SnakeYAML parser
│   │   ├── WorkflowAst.kt              — Workflow, Step, Selector, ActionType
│   │   └── exceptions.kt               — YamlParseException, etc.
│   ├── engine/
│   │   ├── ExecutionEngine.kt          — Step loop orchestrator
│   │   ├── ActionDispatcher.kt         — Handler registry
│   │   ├── ActionContext.kt            — AutomationBridge, SelectorEngine, etc.
│   │   ├── ErrorHandler.kt             — Retry/timeout/policy model
│   │   └── models.kt                   — ExecutionResult, StepResult
│   ├── selector/
│   │   ├── SelectorEngine.kt           — Multi-strategy fallback chain
│   │   ├── Selector.kt                 — Sealed class hierarchy
│   │   ├── Strategy.kt                 — Strategy interface + impls
│   │   └── ResolveResult.kt            — Result with strategy tracking
│   └── service/
│       ├── AutomationService.kt        — AccessibilityService subclass
│       └── EventBus.kt                 — Typed event publish/subscribe
├── res/
│   ├── xml/
│   │   └── accessibility_service_config.xml
│   └── drawable/
│       └── ic_notification.xml
└── test/java/com/proj/automation/
    ├── parser/
    │   └── YamlParserTest.kt
    ├── selector/
    │   └── SelectorEngineTest.kt
    └── engine/
        └── ExecutionEngineTest.kt
```

## Base Application Class

```kotlin
package com.proj.automation

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
```

## Customization Guide

| Parameter | Default | Notes |
|-----------|---------|-------|
| `namespace` | `com.proj.automation` | Change to match your org |
| `applicationId` | `com.proj.automation` | Must match namespace |
| `minSdk` | 26 | Cannot be lowered — foreground service requires API 26+ |
| `targetSdk` | 34 | Update when targeting newer Android versions |
| `compileSdk` | 34 | Use latest stable SDK platform |
| AGP version | 8.2.2 | Pin to specific version for reproducibility |
| Kotlin version | 1.9.22 | Align with AGP compatibility matrix |
| Compose compiler | 1.5.8 | Must match Compose BOM version |
| SnakeYAML version | 2.2 | Use 2.x for security fixes (CVE-2017-18640 patch) |
| `notificationTimeout` | 100ms | Reduce for faster event processing; do not go below 50ms |
| `accessibilityEventTypes` | `typeWindowStateChanged\|typeWindowContentChanged\|typeWindowsChanged` | Add `typeViewClicked`, `typeViewTextChanged` if monitoring UI interactions |

## Validation

Before proceeding with implementation, verify:

- [ ] `./gradlew assembleDebug` builds without errors
- [ ] `minSdk = 26` is set in `build.gradle.kts`
- [ ] `AndroidManifest.xml` declares the Automation Service with `BIND_ACCESSIBILITY_SERVICE` permission
- [ ] `res/xml/accessibility_service_config.xml` exists with required attributes
- [ ] `canPerformGestures="true"` and `canRetrieveWindowContent="true"` are set
- [ ] Foreground service permissions are declared (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`)
- [ ] Compose is enabled in `buildFeatures`
- [ ] Kotlin JVM target is set to 17
- [ ] SnakeYAML 2.x is declared as a dependency
- [ ] Project structure matches the `editor/`, `parser/`, `engine/`, `selector/`, `service/` package layout
- [ ] Unit test dependencies (JUnit 5, MockK, coroutines-test) are declared
