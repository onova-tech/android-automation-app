# Feature Specification: Plugin classification by the base app

**Feature Branch**: `feature/plugin-classification` (merged, PR #8)

**Created**: 2026-10-01

**Status**: Implemented (JVM); admin UI written, not run on a phone

**Input**: A plugin's self-declared category cannot be trusted: an author could declare
`utility` to avoid the financial rules. The phone must decide, with two separate rules: one that
classifies a plugin as financial from a list of known apps, and one that requires a trusted
signer when a plugin asks for secrets.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A plugin that lies about its category is caught (Priority: P1)

A plugin declares `category: utility` but operates `com.nu.production`. The phone treats it as
financial.

**Independent Test**: `ClassificationTest`.

**Acceptance Scenarios**:

1. **Given** the plugin is unsigned or signed by an unknown key, **Then** install is blocked.
2. **Given** it is signed by a trusted key, **Then** it installs, but every command's risk is
   raised to 5 (its own claims, including read-only, are not trusted).
3. **Given** it has interrupt rules, **Then** it is blocked whatever its signer.

### User Story 2 - The owner keeps the list of financial apps (Priority: P1)

The list ships with confirmed package names only (`com.nu.production`). The owner adds or
removes apps in admin mode; installed plugins are reclassified immediately.

**Acceptance Scenarios**:

1. **Given** a plugin for `com.example.smallbank` installed as non-financial, **When** the owner
   adds that package to the list, **Then** the plugin is reclassified; if its signer is not
   trusted it stops loading and is reported in the admin screen.

### User Story 3 - Plugins with secrets need a trusted signer (Priority: P1)

A plugin that declares secrets or `device_credential_prompt` needs a trusted signer but is not
financial (for example, it may still have interrupt rules).

### Edge Cases

- A plugin declaring itself `financial` with `scope: read_only` and caught by nothing else:
  commands at risk 4.
- Removing a trusted key: plugins that needed it stop loading.

## Requirements *(mandatory)*

- **FR-001 (Rule 1)**: A plugin MUST be financial if it declares `category: financial` **or** any
  app in its `ui_automation` or `read_screen` is on the phone's financial-apps list.
- **FR-002**: Financial plugins MUST need a trusted signer, MUST NOT have interrupt rules, and
  MUST get risk ≥ 5 on every command (≥ 4 only when they declared financial and read-only).
- **FR-003 (Rule 2)**: A plugin that declares secrets or `device_credential_prompt` MUST need a
  trusted signer, without becoming financial.
- **FR-004**: The financial-apps list MUST be local, edited only in admin mode, and default to
  confirmed packages only.
- **FR-005**: Installed plugins MUST be reclassified on every reload (list or trusted keys
  changed, app start); non-qualifying plugins stop loading and are shown as problems.
- **FR-006**: The install dialog MUST show the classification and its reasons.

## Success Criteria *(mandatory)*

- **SC-001**: No test lets a plugin operating a listed app install without a trusted signer.
- **SC-002**: A plugin's declared category can only make the result stricter.

## Assumptions

- Checking declared apps is enough because the runtime guard (002) makes a plugin blind to every
  app it did not declare.
- Android has no reliable offline "finance" attribute, so the list is local data.
