# Tasks: Interrupt rules and replay tests

- [X] T001 Engine with injectable clock and sleep; `DevicePort` everywhere (prerequisite for virtual time)
- [X] T002 [US2] `ScreenDevice` over `uiautomator` fixtures with transitions in `core/.../replay/ScreenDevice.kt`
- [X] T003 [US2] Replay runner and test format in `core/.../replay/Replay.kt`; `agp test`
- [X] T004 [US2] WhatsApp replay tests and synthetic fixtures (now in the plugins repository; test copy in `core/src/test/resources/whatsapp`)
- [X] T005 [US1] `interrupts.yaml` parsing with the restricted step set
- [X] T006 [US1] Rule checks before screen actions, chaining ≤ 3, one retry, `max_per_run`
- [X] T007 [US1] Reject interrupt rules in financial plugins (see 007 for classification)
- [X] T008 Fix found by replay: conditions could read unapproved apps → plugins are blind to them
- [X] T009 Fix found by replay: exact hints now filtered by role
- [ ] T010 Recorder mode on the device: save redacted snapshots plus the successful actions as fixtures
