---
description: Start work on a new ticket with proper workflow
argument-hint: [$1]
allowed-tools: [Read, Write, Edit, Bash, Grep, Glob]
---

You are starting work on a new ticket.

**Workflow Authority**: This harness command provides execution steps. CONTRIBUTING.md is the northstar for conventions (branch naming, commit format, SAFe patterns). Follow both:

## Pre-Flight Checklist

1. **Ticket Exists?**
   - If no ticket number provided in arguments, ask user for ticket number
   - Verify ticket exists in local ticket system
   - Confirm ticket status (Todo, In Progress)

2. **Stop-the-Line: AC/DoD Check** (MANDATORY)
   - Verify ticket has **Acceptance Criteria** or **Definition of Done**
   - If AC/DoD is missing or unclear:
     - **STOP** - Do not proceed with implementation
     - Route back to BSA/POPM to define AC/DoD
     - Dev agents are NOT responsible for inventing AC/DoD
   - Work begins ONLY when AC/DoD exists

3. **Branch Naming**
   - Format: `{{TICKET_PREFIX}}-{number}-{short-description}`
   - Must start with {{TICKET_PREFIX}}- and ticket number
   - Use lowercase with hyphens

4. **Start from Latest Dev**
   - Ensure starting from clean dev branch: `git checkout dev && git pull origin dev`
   - Verify no uncommitted changes

5. **Create Feature Branch**
   - Create branch: `git checkout -b $1-{number}-{description}`
   - Confirm branch created successfully

## Workflow

If argument provided ($1):

- Use as ticket prefix (e.g., `/start-work WOR` → WOR-number)
- Ask user for ticket number
- Load ticket details from local ticket system
- Suggest branch name based on ticket title
- Execute checkout workflow

If no argument:

- Ask user for ticket prefix ($1)
- Ask user for ticket number ($2)
- Proceed with workflow

## Success Criteria

- ✅ Ticket verified
- ✅ AC/DoD confirmed (Stop-the-Line gate passed)
- ✅ On latest dev branch
- ✅ Feature branch created with correct naming
- ✅ Ready to begin work

Report status and any blockers. If AC/DoD is missing, report blocker and route to BSA.
