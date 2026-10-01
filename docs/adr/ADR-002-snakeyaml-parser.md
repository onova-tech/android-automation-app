# ADR-002: SnakeYAML 2.x as the YAML Parsing Library

| Field | Value |
|-------|-------|
| **ADR** | ADR-002 |
| **Status** | Accepted |
| **Date** | 2026-07-06 |
| **Context** | PROJ-000 Phase 1 POC — YAML workflow parsing for Android Automation Runtime |
| **Deciders** | System Architect, BSA |

## Context

The POC must parse declarative workflow files written in YAML into a typed AST. Three approaches were evaluated:

1. **SnakeYAML** — battle-tested, small footprint, used by Jenkins/Ansible
2. **Jackson YAML (`jackson-dataformat-yaml`)** — good Kotlin integration, larger footprint
3. **Custom parser** — full control but significant development time

The POC scope is simple linear workflows (no expressions, no variables in Phase 1). The choice needs to balance reliability, size, and development time.

## Decision

**SnakeYAML 2.x is selected** as the YAML parsing library.

### Rationale

| Criterion | SnakeYAML 2.x | Jackson YAML | Custom Parser |
|-----------|--------------|--------------|---------------|
| Maturity | 10+ years, widely used | High | N/A |
| APK footprint | ~250KB | ~500KB | N/A |
| Android compatibility | Excellent | Good | Full control |
| Expression support | None needed (Phase 2) | None needed | Full control |
| Development time | Low (mature API) | Low-Medium | High |
| Security | 2.x fixed CVE-2017-18640 | N/A | N/A |
| Kotlin integration | Works (no native) | Works (no native) | Full control |

### Why Not Jackson YAML

Jackson YAML was considered but SnakeYAML was preferred because:
- Smaller APK footprint (~250KB vs ~500KB)
- The expression engine (`${variable}` syntax) is a Phase 2 concern
- SnakeYAML's event-driven API is sufficient for linear workflow parsing
- The Phase 0 feasibility study already validated SnakeYAML on Android

### Why Not Custom Parser

A custom parser would provide full control but adds:
- 3-5 days of development time
- Ongoing maintenance burden
- Risk of edge-case bugs in YAML edge cases (anchors, multi-doc, special chars)

For a 15-day POC, this overhead is not justified.

## Security Considerations

SnakeYAML 2.x removed the auto-tagging vulnerability (CVE-2017-18640). The POC uses SnakeYAML 2.2 with `LoaderOptions` configured to:
- Disable multiple document streams
- Disable duplicate keys
- Allow aliases (safe in 2.x)

## Consequences

### Positive

- Smallest footprint of the three options
- Battle-tested reliability
- No expression engine needed for POC scope
- Simple configuration with `LoaderOptions`

### Negative

- No built-in expression support (acceptable — Phase 2 concern)
- Custom deserialization for polymorphic action fields requires work

## References

- Architecture Review, Section 3.2: Technology Recommendations — YAML Library
- SnakeYAML 2.x changelog: [CVE-2017-18640 fix](https://github.com/yaml/snakeyaml/releases)
- Phase 0 feasibility study confirmed SnakeYAML works on Android
