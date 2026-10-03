# Tasks: `.agp` plugin packages

**Input**: [spec.md](spec.md), [plan.md](plan.md), [contracts/package-format.md](contracts/package-format.md)

## Phase 1: Package core

- [X] T001 Safe in-memory unpacker with path, type and size limits in `core/.../plugin/PackageReader.kt`
- [X] T002 `PACKAGE.lock` format and verification in `core/.../plugin/PackageLock.kt`
- [X] T003 Reproducible zip writer in `core/.../plugin/PackageZip.kt`
- [X] T004 Manifest parsing (schema 1) in `core/.../plugin/Manifest.kt`

## Phase 2: User Story 1 — author and build (P1)

- [X] T005 [US1] Loader: merge files, check references, reject recursion and duplicates in `PluginLoader.kt`
- [X] T006 [US1] Library vendoring and namespacing in `PackageBuilder.kt`
- [X] T007 [US1] `agp validate`, `build`, `inspect`, `targets` (now `agp/` in the plugins repository)
- [X] T008 [US1] Example plugin `whatsapp` and library `android-common` (now in the plugins repository; test copy in `core/src/test/resources/`)

## Phase 3: User Story 2 — install safely (P1)

- [X] T009 [US2] Risk floor and install summary
- [X] T010 [US2] Malicious-zip tests in `PluginPackageTest`
- [X] T011 [US2] Admin-screen install flow with summary and device credential (see 003)

## Phase 4: User Story 3 — capabilities (P1)

- [X] T012 [US3] `CapabilityGuard`: blind to unapproved apps; `E_CAPABILITY` on launch/links
- [X] T013 [US3] Tests for escape attempts (actions, conditions, links)

## Phase 5: Remaining

- [ ] T014 Update flow: per-file diff and re-approval when capabilities, secrets or target app change
- [ ] T015 Uninstall wipes secrets, cache entries and pending jobs (with 008)
- [ ] T016 Validate the WhatsApp plugin hints with `agp targets` on dumps from the agent phone
- [ ] T017 Library `overrides:` listed in the install summary (deferred)
