# Tasks: Plugin package signing

- [X] T001 [US1] `PackageSignature`: sign, verify, fingerprint, `signPackage` in `core/.../plugin/PackageSignature.kt`
- [X] T002 [US1] `PACKAGE.sig` excluded from the lock; `PackageBuilder` `signingKey`
- [X] T003 [US1] `agp keygen`, `sign`, `verify`, `fingerprint`, `build --key`; encrypted key files in `KeyFiles.kt`
- [X] T004 [US2] `SignatureStatus` in `Plugin`; install summary shows signer or "Signature: NONE"
- [X] T005 [US2][US3] `InstallPolicy` with warnings, trusted keys and signer continuity
- [X] T006 [US3] App records the signer per plugin and re-checks it on every load
- [X] T007 [US2] Admin screen: trusted developers section; trust a key from the install dialog
- [X] T008 Tests in `SigningTest`; `*.key` in `.gitignore`
- [ ] T009 Validate the install dialog states on the agent phone
