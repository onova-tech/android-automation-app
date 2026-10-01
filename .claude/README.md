# Claude Code Configuration

This directory contains the {{PROJECT_SHORT}} Claude Code harness: hooks, slash commands, and (coming soon) skills for workflow automation.

## OpenCode Integration Guide

> **Note**: All placeholders in this guide ({{TICKET_PREFIX}}, {{TICKET_NUMBER}}, etc.) should be replaced with command-line parameters:
> - {{TICKET_PREFIX}} → $1
> - {{TICKET_NUMBER}} → $2

This harness is **provider-agnostic** and works with:
- **Claude Code**: Via `.claude/` configuration (native)
- **Augment**: Via `.augment/` configuration 
- **OpenCode**: Via `.opencode/` configuration wrapper

The **core workflow engine** (.claude/commands, .claude/skills, .claude/agents) is the same for all providers. Only provider-specific configuration differs.

### Quick Integration Options (Minimal Model Effort)

#### Option 1: Copy Path (Recommended)
```bash
# One-time integration - configure OpenCode:
mkdir -p .opencode
cp -r .claude/commands/.opencode/
cp -r .claude/skills/.opencode/
cp -r .claude/agents/.opencode/
cp .claude/team-config.json .opencode/
cp .claude/settings.template.json .opencode/settings.local.json

# Update settings for OpenCode:
nano .opencode/settings.local.json
```

**Edit .opencode/settings.local.json:**
```json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "0"
  },
  "teammateMode": "in-process",
  "permissions": {
    "_comment": "Configure OpenCode permission rules for your project",
    "AllowFileRead": {
      "description": "Allow reading files in .opencode directory",
      "resources": [".opencode/**"]
    },
    "AllowFileWrite": {
      "description": "Allow writing to .opencode directory",
      "resources": [".opencode/**"]
    }
  }
}
```

#### Option 2: Symbolic Links (Zero Code Changes)
```bash
# Zero model effort - preserve original files:
mkdir -p .opencode
ln -sf $(pwd)/.claude/commands .opencode/
ln -sf $(pwd)/.claude/skills .opencode/
ln -sf $(pwd)/.claude/agents .opencode/
ln -sf $(pwd)/.claude/team-config.json .opencode/
ln -sf $(pwd)/.claude/settings.template.json .opencode/settings.local.json
```

**Key Notes:**
- Choose any provider option - functionality remains identical
- 24 commands, 18 skills, 11 SAFe agents, evidence tracking unchanged
- 3-stage PR validation pipeline intact
- All quality gates and blocking checks preserved
> **Key Integration Tips:**
> - Commands expect ticket numbers, NOT full ticket prefixes
> - Arguments follow: `command "WOR"` (prefix) then ticket number or branch 
> - Example: `/start-work WOR` (prefix) → user provides ticket number
> - See `/start-work` docs for parameter details

***Pro Tip:*** The workflow functionality is **100% identical** with OpenCode. Only configuration location changes.

**Next Steps for Users:**
1. Choose your integration option above
2. Execute the commands to setup `.opencode/` configuration
3. Customize `.opencode/settings.local.json` permission rules as needed
4. Run OpenCode with your new `.opencode/` configuration
5. All SAFe workflow functionality is now available in OpenCode

## Harness Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│                      {{PROJECT_SHORT}} Claude Code Harness                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  HOOKS (Guardrails)              SLASH COMMANDS (User-Invoked)        │
│  ├─ Pre-commit reminders         ├─ /start-work                       │
│  ├─ Push blocker (uncommitted)   ├─ /remote-deploy                    │
│  └─ Auto-format on edit          └─ /pre-pr                           │
│                                                                       │
│  SKILLS (Model-Invoked) ✅ Available                                  │
│  ├─ safe-workflow      (SAFe commit/PR patterns)                      │
│  ├─ pattern-discovery  (search docs/patterns first)                   │
│  ├─ rls-patterns       (database security helpers)                    │
│  └─ frontend-patterns  (Clerk, shadcn, Next.js App Router)            │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

**Key distinction:**

- **Hooks**: Automatic guardrails (reminders and critical blockers)
- **Slash Commands**: Explicit user-invoked workflows (`/start-work`, `/pre-pr`)
- **Skills**: Model-invoked expertise packs (Claude loads this automatically)

## Team Principles (SAFe + Round Table)

This harness is designed to help every teammate (human + AI) uphold:

- **SAFe Pillars**: Alignment, Built-in Quality, Program Execution, Transparency
- **{{PROJECT_SHORT}} Round Table**: humans + AI agents are peers

Canonical reference: `.cursor/rules/06-team-culture.mdc`

## Role Execution Modes ($1-499)

### Collapsed vs Separated Roles

The vNext workflow defines role separation (Implementation → QAS → RTE → HITL), but roles can be **collapsed** for efficiency when appropriate.

**Key principle**: Subagents are for efficiency _and_ independence; only coordination roles may be collapsed.

### Role Classification

## Documentation Templates

| Agent                                  | Output Directory                        | Naming Convention                 |
| -------------------------------------- | --------------------------------------- | --------------------------------- |
| **QAS** (Quality Assurance Specialist) | `/docs/agent-outputs/qa-validations/`   | `{{TICKET_PREFIX}}-{number}-qa-validation.md`   |
| **BSA** (Business Systems Analyst)     | `/docs/agent-outputs/requirements/`     | `{{TICKET_PREFIX}}-{number}-requirements.md`    |
| **System Architect**                   | `/docs/adr/`                            | `ADR-{number}-{title}.md`         |
| **Tech Writer**                       | `/docs/agent-outputs/technical-docs/`   | `{{TICKET_PREFIX}}-{number}-technical-docs.md`  |
| **Data Engineer**                      | `/docs/agent-outputs/technical-docs/`   | `{{TICKET_PREFIX}}-{number}-migration-plan.md`  |
| **TDM** (Technical Delivery Manager)   | `/docs/agent-outputs/delivery-reports/` | `{{TICKET_PREFIX}}-{number}-delivery-report.md` |

## ✅ Mandatory Reading Checklist

**Before starting ANY task**, agents must check:

### Database Work?

- [ ] Read `/docs/database/DATA_DICTIONARY.md` (MANDATORY)
- [ ] Read `/docs/database/RLS_DATABASE_MIGRATION_SOP.md` (if schema changes)
- [ ] Check RLS implications

### New Service/Feature?

- [ ] Read `/docs/guides/SECURITY_FIRST_ARCHITECTURE.md` (MANDATORY)
- [ ] Review security patterns

### Pattern Work?

- [ ] Check `/patterns_library/` for existing patterns FIRST
- [ ] Reuse before creating new patterns

## Documentation

- **Harness Audit**: `docs/agent-outputs/workflow-analysis/HARNESS_AND_SKILLS_AUDIT_2025-12-18.md`
- **CONTRIBUTING.md**: Project workflow requirements
- **Slash Commands Guide**: [Claude Code slash commands](https://docs.anthropic.com/en/docs/claude-code/slash-commands)
- **Hooks Guide**: [Claude Code hooks](https://docs.anthropic.com/en/docs/claude-code/hooks)

---

**Last Updated**: 2026-01-04
**Maintained by**: {{PROJECT_SHORT}} Development Team + ARCHitect-in-the-IDE (Auggie)
