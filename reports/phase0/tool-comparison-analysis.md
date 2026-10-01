# Tool Comparison Analysis — Android Automation Tools

**Date**: 2026-07-06  
**Context**: PROJ-000 Phase 0 Feasibility Study  

---

## Evaluation Criteria

| Criteria | Weight | Description |
|----------|--------|-------------|
| Reliability | 25% | How consistently does it work across apps/devices? |
| Setup Complexity | 15% | How hard to get running? |
| Maintenance Burden | 15% | Ongoing effort to keep working |
| Flexibility | 15% | How easy to extend/customize? |
| Local-First | 15% | Can it run fully offline on-device? |
| Open Source | 10% | License and community access |

---

## Tool Evaluations

### 1. AndroidX UI Automator

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 9/10 | Google-maintained, battle-tested |
| Setup Complexity | 6/10 | Requires Android Studio + Gradle |
| Maintenance Burden | 8/10 | Stable API, infrequent changes |
| Flexibility | 7/10 | Good API, but tied to testing framework |
| Local-First | 10/10 | Runs entirely on-device |
| Open Source | 10/10 | Apache 2.0 |
| **Weighted** | **8.35/10** | **STRONGEST CANDIDATE** |

**Architecture**: Uses `AccessibilityNodeInfo` directly via the `uiautomator` framework. Provides `UiDevice`, `UiSelector`, `UiObject2` abstractions.

**Relevance to PROJ-000**: **HIGH** — Directly uses the same API surface we need. Study `UiSelector` for our selector design.

**Key Learnings**:
- `UiSelector` supports text, resourceId, className, contentDescription matching
- `findObject()` with timeout pattern is exactly our `wait_for` concept
- `getChildren()` enables tree traversal
- `performAction()` maps to our action model

### 2. Appium

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 6/10 | WebDriver overhead causes flakiness |
| Setup Complexity | 3/10 | Complex server + client + device setup |
| Maintenance Burden | 4/10 | Frequent dependency updates, version drift |
| Flexibility | 8/10 | Large ecosystem, many bindings |
| Local-First | 7/10 | Client-server but runs locally |
| Open Source | 10/10 | Apache 2.0 |
| **Weighted** | **6.15/10** | Overkill for our use case |

**Architecture**: WebDriver protocol → Appium Server → Device (UI Automator/XCUITest).

**Relevance to PROJ-000**: **MEDIUM** — Study element locator strategy, but avoid server architecture.

**Key Learnings**:
- Implicit wait / explicit wait patterns inform our timeout model
- `FindStrategy` class shows how to chain locator strategies
- Mobile-specific locators (AndroidUIAutomator, accessibilityId) are useful
- Network overhead makes it unsuitable for our local-first requirement

### 3. Auto.js / Auto.js Pro

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 6/10 | Works on many apps, but inconsistent |
| Setup Complexity | 8/10 | Simple app install |
| Maintenance Burden | 5/10 | Project declining, API changes |
| Flexibility | 7/10 | JavaScript, good API |
| Local-First | 10/10 | Runs on-device |
| Open Source | 8/10 | Pro version is paid |
| **Weighted** | **7.20/10** | **GOOD REFERENCE** |

**Architecture**: Accessibility Service + embedded JavaScript engine.

**Relevance to PROJ-000**: **HIGH** — Best reference for selector design and accessibility tree traversal.

**Key Learnings**:
- `$id()`, `$text()`, `$desc()`, `$className()` selectors are clean
- Auto-wait for elements before clicking (auto-idle)
- Accessibility event listening for UI changes
- Shadow DOM equivalent via `deepFind()`

### 4. Tasker + AutoInput

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 7/10 | Production-tested, many users |
| Setup Complexity | 5/10 | Tasker + AutoInput plugin |
| Maintenance Burden | 7/10 | Stable, infrequent updates |
| Flexibility | 6/10 | Proprietary plugin format |
| Local-First | 10/10 | Runs entirely on-device |
| Open Source | 2/10 | Paid, closed source |
| **Weighted** | **6.55/10** | **GOOD REFERENCE** |

**Relevance to PROJ-000**: **MEDIUM** — Study action model and workflow patterns.

**Key Learnings**:
- Profile-based automation (similar to our YAML workflows)
- Rich action set: tap, type, press key, wait, scroll
- Variable interpolation in actions
- Error handling with "if field exists" checks

### 5. ADB (Android Debug Bridge)

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 7/10 | Reliable for shell commands |
| Setup Complexity | 7/10 | Easy for developers |
| Maintenance Burden | 6/10 | Stable |
| Flexibility | 5/10 | Limited to ADB commands |
| Local-First | 10/10 | Fully local |
| Open Source | 10/10 | Part of Android SDK |
| **Weighted** | **7.20/10** | **USEFUL COMPLEMENT** |

**Relevance to PROJ-000**: **MEDIUM** — Complementary for system-level operations.

**Key Learnings**:
- `adb shell input tap x y` for screen coordinates
- `adb shell am start` for launching apps
- `adb shell pm` for package management
- `adb shell service call` for system services
- Not a replacement for Accessibility Services (no tree access)

### 6. Scrcpy

| Criteria | Score | Notes |
|----------|-------|-------|
| Reliability | 8/10 | Very stable screen mirroring |
| Setup Complexity | 7/10 | Simple one-command setup |
| Maintenance Burden | 8/10 | Stable, active development |
| Flexibility | 4/10 | Screen mirroring only |
| Local-First | 10/10 | Fully local |
| Open Source | 10/10 | MIT License |
| **Weighted** | **7.50/10** | **USEFUL FOR SCREENSHOT** |

**Relevance to PROJ-000**: **LOW-MEDIUM** — Useful for screenshot capture during POC.

---

## Decision Matrix

| Criteria | UI Automator | Appium | Auto.js | Tasker | ADB | Scrcpy |
|----------|-------------|--------|---------|--------|-----|--------|
| Reliability (25%) | 9 | 6 | 6 | 7 | 7 | 8 |
| Setup (15%) | 6 | 3 | 8 | 5 | 7 | 7 |
| Maintenance (15%) | 8 | 4 | 5 | 7 | 6 | 8 |
| Flexibility (15%) | 7 | 8 | 7 | 6 | 5 | 4 |
| Local-First (15%) | 10 | 7 | 10 | 10 | 10 | 10 |
| Open Source (10%) | 10 | 10 | 8 | 2 | 10 | 10 |
| **TOTAL** | **8.35** | **6.15** | **7.20** | **6.55** | **7.20** | **7.50** |

---

## Recommendations for PROJ-000

### Primary Reference
**AndroidX UI Automator** — Study `UiSelector` API, `UiDevice` patterns, and element finding strategies.

### Secondary Reference
**Auto.js** — Study selector syntax (`$id()`, `$text()`, `$desc()`) and accessibility tree traversal.

### Complementary Tool
**ADB** — Use for system-level operations (install, screenshot, permissions).

### Avoid
- Building a WebDriver-like protocol (unnecessary complexity)
- Appium server architecture (violates local-first principle)
- Cloud-based solutions (violates privacy principle)

### Key Design Decisions from Comparison

1. **Selector API**: Follow Auto.js pattern (`$id()`, `$text()`, etc.) translated to YAML
2. **Element Model**: Follow UI Automator's `UiObject2` abstraction
3. **Wait Strategy**: Follow Appium's explicit wait pattern (polling with timeout)
4. **Action Model**: Follow Tasker's profile-based action model
5. **Distribution**: Sideloading (like Auto.js), not Play Store initially

---

*Analysis generated as part of PROJ-000 Phase 0 Feasibility Study*
