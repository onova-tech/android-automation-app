# Tasks: Plugin classification

- [X] T001 [US1] `PluginClassifier` with `KNOWN_FINANCIAL_APPS = {com.nu.production}`, `classify`, `effective`
- [X] T002 [US1] `InstallPolicy`: block financial plugins with interrupt rules; block `needsTrustedSigner` without a trusted key
- [X] T003 [US2] `financialApps` setting; `AgentCoordinator.setFinancialApps`; reclassify on reload; `untrustKey` reloads
- [X] T004 [US2] Admin screen "Financial apps" section; classification in the install dialog
- [X] T005 [US3] Rule 2 for secrets and `device_credential_prompt`
- [X] T006 Tests in `ClassificationTest` (liar plugin, owner list, risk raise, interrupt rules, rule 2, ordinary plugins)
- [ ] T007 Add other confirmed bank package names as the owner validates them
