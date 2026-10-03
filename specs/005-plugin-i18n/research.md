# Research: Per-language plugin texts

## Decision: Flat key/text files per language, referenced with `${t.key}`

- **Rationale**: Reuses the existing template syntax; no new construct. Flat maps are easy to
  diff and validate. Checking key parity at install means a plugin never fails at run time
  because a language is incomplete.
- **Alternatives**: Duplicating skills per language (copy-paste drift); target hints listing all
  languages' labels at once (works for hints, not for typed or expected text, and can match the
  wrong language's element).

## Decision: Device language picks the texts

- **Rationale**: The target app follows the device language, so the plugin must too. Fallback
  `pt-br` → `pt` → default covers regional tags without requiring a file per region.
