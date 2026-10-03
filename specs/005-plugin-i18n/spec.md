# Feature Specification: Per-language plugin texts

**Feature Branch**: `feature/i18n` (merged)

**Created**: 2026-10-01

**Status**: Implemented (JVM)

**Input**: The same plugin must work on a phone set to Portuguese or English: labels the
resolver looks for, intent wording and expected texts change with the device language.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One plugin, several languages (Priority: P1)

A plugin author puts texts in `i18n/pt.yaml` and `i18n/en.yaml` and uses `${t.send_label}` in
targets, steps and rules. On the phone, the device language picks the texts.

**Independent Test**: `I18nTest`; replay `send_english` runs the WhatsApp send skill with
`language: en`.

**Acceptance Scenarios**:

1. **Given** a device in `pt-BR`, **Then** texts come from `pt-br.yaml` if present, else
   `pt.yaml`, else the plugin's `default_language`.
2. **Given** a key present in `pt.yaml` but missing in `en.yaml`, **Then** the package is
   rejected at install.
3. **Given** `${t.unknown}` anywhere in the plugin, **Then** the package is rejected at install.

### Edge Cases

- A skill assigning `t` (`set: { t: … }`): rejected; `t` is reserved.
- No `i18n/` folder: `${t.*}` is not allowed; plugin works with literal texts.

## Requirements *(mandatory)*

- **FR-001**: `i18n/<lang>.yaml` MUST be a flat map of `key: text`; language codes like `pt`,
  `en`, `pt-br`.
- **FR-002**: When `i18n/` exists, `plugin.default_language` MUST be declared and every language
  MUST define the same keys.
- **FR-003**: Every `${t.key}` used in skills, flows, targets, screens and rules MUST exist.
- **FR-004**: Language resolution: exact device tag → base language → default language.
- **FR-005**: Replay tests MAY set `language:`; `agp targets --lang` MUST fill texts for that
  language.

## Success Criteria *(mandatory)*

- **SC-001**: The WhatsApp plugin resolves its targets on both the Portuguese and English
  fixtures.
- **SC-002**: Missing keys or languages never reach run time (rejected at install).

## Assumptions

- Command keywords and verbs stay English and language-independent (003); i18n is about the
  target app's screen.
