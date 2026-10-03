# Feature Specification: `.agp` plugin packages

**Feature Branch**: `feature/engine-foundation` (merged, PR #2)

**Created**: 2026-09-30

**Status**: Implemented (JVM). Admin-mode install UI written (003); secrets pending (008)

**Input**: Add support for a new app by installing one plugin package — a zip of YAML files with
no code — without changing the base app. The plugin says *what* the app offers; the base app
knows *how* to do everything.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Author and build a plugin (Priority: P1)

A developer writes a plugin as a folder of small YAML files (manifest, commands, skills, flows,
targets, screens), validates it, and builds a reproducible `.agp` with the `agp` tool.

**Why this priority**: Without packages there is nothing for the agent to run.

**Independent Test**: `agp validate` and `agp build` on `plugins/whatsapp`; building twice
gives the same bytes and the same package hash.

**Acceptance Scenarios**:

1. **Given** a folder with a broken `call` reference, **When** `agp validate` runs, **Then** it
   reports the missing flow and exits non-zero.
2. **Given** a plugin that uses library `android-common@0.1.0`, **When** it is built, **Then**
   the library is copied into `lib/android-common/` and pinned in `PACKAGE.lock`.

### User Story 2 - Install a plugin safely on the phone (Priority: P1)

The owner puts a package on the phone and, in admin mode, sees an install summary (target app,
capabilities, commands with risk, secrets, libraries, package hash) before approving it.

**Why this priority**: The phone may hold bank credentials; installing a package is the main
supply-chain risk.

**Independent Test**: `PluginPackageTest` with malicious zips (path traversal, zip bombs,
duplicates, unlisted files, hash mismatch).

**Acceptance Scenarios**:

1. **Given** a zip with `../evil.yaml`, **Then** it is rejected before any content is read.
2. **Given** a package where a file changed after the build, **Then** it is rejected because the
   file no longer matches `PACKAGE.lock`.

### User Story 3 - Plugins only touch what was approved (Priority: P1)

At run time a plugin can only see and operate the apps, and open the links, its owner approved.

**Independent Test**: Capability tests where a skill tries to read or click another app, or
open a URL outside its allowlist.

**Acceptance Scenarios**:

1. **Given** a plugin approved for `com.whatsapp`, **When** the foreground app is another app,
   **Then** actions and conditions see an empty screen (the plugin is blind to it).
2. **Given** `open_url` to a link outside `deeplinks`, **Then** the run ends with `E_CAPABILITY`,
   which `try` cannot catch.

### User Story 4 - Reuse flows and libraries (Priority: P2)

Flows are reused within a plugin; common Android handling (permission dialogs, keyboard) is
shared through vendored libraries.

**Acceptance Scenarios**:

1. **Given** a library flow `dismiss_permission`, **Then** it is callable as
   `android-common.dismiss_permission` and runs with the calling plugin's capabilities.
2. **Given** a plugin name that clashes with a library name, **Then** loading fails (no silent
   override).

### Edge Cases

- Unknown `schema` major in `plugin.yaml`: rejected.
- Recursive flow calls: rejected at load.
- Duplicate names across files, or case-colliding paths: rejected.
- A skill declaring a risk lower than the floor: the floor wins.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A package MUST be a zip (`.agp`) containing only: `.yaml`/`.yml` files in their
  folders, `.json`/`.xml` only under `fixtures/`, `README.md`, the generated `PACKAGE.lock` and
  an optional `PACKAGE.sig` (006). Anything else MUST be rejected.
- **FR-002**: The unpacker MUST read in memory only, and reject absolute paths, `..`, symbolic
  links, duplicate or case-colliding names, more than 500 entries, files over 512 KB, and more
  than 8 MB in total — checked before reading content.
- **FR-003**: `PACKAGE.lock` MUST list every other file's SHA-256 and the vendored library
  versions; the package hash (SHA-256 of the lock) is what the owner approves.
- **FR-004**: The manifest MUST declare id, name, version, category, target app package and
  capabilities (`ui_automation`, `read_screen`, `deeplinks`, notifications, contacts,
  `sms_reply`, `device_credential_prompt`, secrets).
- **FR-005**: The loader MUST merge all files into one model, check every reference (`call`,
  targets, screens, commands → skills) and reject recursion and duplicate names.
- **FR-006**: Every command MUST get the effective risk `max(floor, declared)`; the floor is
  computed by the base app (financial: 5, or 4 if read-only; secrets or device PIN: 4;
  messaging: 2; otherwise 1) and raised by classification (007).
- **FR-007**: At run time, a `CapabilityGuard` MUST make a plugin blind to unapproved apps and
  abort with `E_CAPABILITY` on launches or links outside its lists.
- **FR-008**: Libraries MUST hold only flows, targets, screens and interrupt rules — no
  capabilities, secrets or commands — and MUST be vendored at build time in v1.
- **FR-009**: The `agp` tool MUST provide `validate`, `build` (reproducible), `inspect` and
  `targets <dump.xml>` (resolve every named target against a real screen and explain how).
- **FR-010**: Install, update and uninstall MUST happen only in admin mode on the phone; an
  update that adds capabilities or secrets requires re-approval; uninstall wipes the plugin's
  secrets.

### Key Entities

- **Package / PackageLock / Manifest / Plugin**: see [data-model.md](data-model.md).
- **Library**: versioned set of flows, targets, screens and rules, namespaced by id.

## Success Criteria *(mandatory)*

- **SC-001**: Building the same source twice yields byte-identical packages.
- **SC-002**: Every malicious-zip case in the tests is rejected.
- **SC-003**: No test lets a plugin read, click or open anything outside its capabilities.
- **SC-004**: The example WhatsApp plugin passes `agp validate` and its replay tests (004).
- **SC-005** *(device, open)*: The WhatsApp plugin's targets resolve on real screen dumps from the
  agent phone (`agp targets`).

## Assumptions

- Plugins are installed from a file (adb push or file picker); there is no plugin store and no
  remote install.
- Library `overrides:` and shared (non-vendored) libraries are deferred.
- Version drift handling (`tested_versions`, screen-signal checks for server-driven UIs) is
  specified with the first financial plugin (009).
