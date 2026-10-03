# Data model: plugin packages

| Entity | Fields | Rules |
|--------|--------|-------|
| **Package** (`.agp`) | zip entries (path → bytes) | Read in memory with the limits in FR-002 |
| **PackageLock** | `files: path → sha256`, `libraries: id → version` | Sorted text; covers every file except itself and `PACKAGE.sig` |
| **Package hash** | SHA-256 of `PACKAGE.lock` | Shown at install; signed in 006 |
| **Manifest** (`plugin.yaml`) | `schema`, `plugin {id, name, version, category, scope, default_language}`, `app {package, tested_versions}`, `capabilities`, `secrets[]`, `libraries` | Unknown schema major rejected |
| **Command** | `verb`, `skill`, `args` template, effective `risk` 0–5 | Risk = max(floor, declared) |
| **Skill** | `params`, `steps`, `return`, optional `risk` | Public; reached through commands |
| **Flow** | `params`, `steps` | Private; reached through `call`; library flows namespaced `lib.name` |
| **Named target** | intent, role, hints, region | Referenced by name from any step |
| **Screen** | `signals` (conditions) | Used by `screen_is` |
| **Plugin** (loaded) | manifest + commands + skills + flows + targets + screens + rules + strings + package hash + signature | Immutable after load |
| **Installed record** (app) | id, package hash, signer fingerprint, file | Stored in `plugins.json`; re-checked on every load |
