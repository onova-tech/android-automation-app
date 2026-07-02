# Safeworkflow Ticket System

## Overview

This directory contains the local ticket system used by SAFe agents to track work progress, acceptance criteria, and evidence. This replaces the Linear ticket system with a file-based approach.

## Ticket File Structure

Each ticket is stored as a markdown file named `{{TICKET_PREFIX}}-{{number}}.md`.

### Required Fields

Every ticket file must contain:

```markdown
# {{TICKET_PREFIX}}-{{number}}: {{Ticket Title}}

## Status
Status: [Todo | In Progress | Ready for Review | Blocked | Done]

## Created
Created: [YYYY-MM-DD HH:MM]

## Updated
Updated: [YYYY-MM-DD HH:MM]

## Work Summary
[Brief description of the ticket work]

## Acceptance Criteria (AC)

### Required

- [ ] [Acceptance Criterion 1]
- [ ] [Acceptance Criterion 2]
- [ ] [Acceptance Criterion 3]

### Optional

- [ ] [Nice-to-have feature]
- [ ] [Documentation requirement]

## Definition of Done (DoD)

### Required

- [ ] All acceptance criteria met
- [ ] Testing completed (unit, integration, E2E)
- [ ] Code review completed
- [ ] Documentation updated
- [ ] Evidence captured

## Progress

### Current Status
- [% Done] complete

### Work Completed
- [ ] [Task 1]
- [ ] [Task 2]
- [ ] [Task 3]

### Next Steps
1. [ ] [Next Task 1]
2. [ ] [Next Task 2]

### Blockers
- [ ] [Blocker if any]

### Timeline

#### Started
[Date and time]

#### Current
- % complete
- Est. completion: [Date/time]

## Evidence

### Command Output

```bash
# Validate implementation
yarn test:integration && yarn type-check && yarn lint
```

### Test Results

```
Test Results:
- Unit tests: X passed
- Integration tests: Y passed
- E2E tests: Z passed

Overall Result: [SUCCESS or FAILED]
```

### Documentation

- [ ] API documentation updated
- [ ] README updated
- [ ] Inline comments
- [ ] Architecture diagrams

### Code Quality

- [ ] TypeScript validation passed
- [ ] ESLint checks passed
- [ ] Prettier formatting
- [ ] No console.log statements

## Artifacts

### Session IDs

- Agent session: [claude_session_id]
- Commits: [commit_hash_1, commit_hash_2]
- Validation runs: [validation_output]

### Related PRs

- PR: #[number] - Title
- PR: #[number] - Title

### Files Changed

- `path/to/file1.ext`
- `path/to/file2.ext`
- `path/to/file3.ext`

## Dependencies

### Technical Enablers

- Dependency 1: [Description]
- Dependency 2: [Description]
- Dependency 3: [Description]

## Notes

### Decisions

- Decision 1: [Rationale]
- Decision 2: [Rationale]

### Questions

- Question 1: [Answer]
- Question 2: [Answer]

### Feedback

## Template Usage

### Create New Ticket

```bash
cat > safeworkflow/tickets/{{TICKET_PREFIX}}-123-My-Feature.md << 'EOF'
# {{TICKET_PREFIX}}-123: My Feature

## Status
Status: Todo

## Created
Created: 2026-01-02 14:15

## Updated
Updated: 2026-01-02 14:15

## Work Summary
Implements feature to allow users to do X, Y, or Z.

## Acceptance Criteria

### Required

- [ ] User can [action] with [input]
- [ ] System validates [input] correctly
- [ ] Error handling works for [invalid input]

## End-of-File
EOF
```

### Update Existing Ticket

```bash
# Add progress comment
echo "- 2026-01-02 14:20:00: Update status to In Progress" >> safeworkflow/tickets/{{TICKET_PREFIX}}-123-My-Feature.md

# Record work completion
echo "- 2026-01-02 15:00:00: Implement core feature" >> safeworkflow/tickets/{{TICKET_PREFIX}}-123-My-Feature.md

echo "Status: In Progress" >> safeworkflow/tickets/{{TICKET_PREFIX}}-123-My-Feature.md
echo "Progress: 60%" >> safeworkflow/tickets/{{TICKET_PREFIX}}-123-My-Feature.md
```

## Migration Instructions

### From Linear

1. **Export Linear ticket data**
2. **Convert to markdown format**
3. **Store in safeworkflow/tickets/ directory**
4. **Update any scripts referencing Linear to use local file system**

### File Naming

- Ticket number: Use same as Linear ticket number
- Prefix: Use your ticket prefix ({{TICKET_PREFIX}})
- Duplicate prevention: Check for existing file first
- File permissions: Ensure agents can read/write

## Integration with SAFe Workflow

### Commands Updated

All SAFe agents now use this ticket system instead of Linear:

- `/start-work`: Verifies ticket file exists
- `/check-workflow`: Checks ticket file for status
- `/end-work`: Records work completion in ticket file
- `start-work`: Suggests tickets based on ticket files

### Evidence Collection

Tickets store all evidence locally:

- Command output captured in ticket file
- Test results stored in ticket
- Artifact links added to ticket
- Validation checkpoints recorded

## Local Ticket Access Script

For quick ticket access:

```bash
# Install script in .claude/scripts/
cat << 'EOF' > .claude/scripts/local-ticket-access.sh
#!/bin/bash
TICKETS_DIR="{{PROJECT_ROOT}}/safeworkflow/tickets"

if [ ! -d "$TICKETS_DIR" ]; then
  echo "❌ Tickets directory not found: $TICKETS_DIR"
  exit 1
fi

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 {{TICKET_PREFIX}}-number"
  echo "Available tickets:"
  ls -1 "$TICKETS_DIR" | head -10
  exit 1
fi

TICKET_FILE="$TICKETS_DIR/$1.md"

if [ ! -f "$TICKET_FILE" ]; then
  echo "❌ Ticket not found: $1.md"
  echo "Available tickets:"
  ls -1 "$TICKETS_DIR"
  exit 1
fi

cat "$TICKET_FILE"
EOF

chmod +x .claude/scripts/local-ticket-access.sh
EOF
```

Then use it:

```bash
# Get ticket details
.local/scripts/local-ticket-access.sh {{TICKET_PREFIX}}-123

# Update ticket status
echo "Status: In Progress" >> safeworkflow/tickets/{{TICKET_PREFIX}}-123.md
```

## Security Considerations

1. **File Permissions**: Ensure proper permissions for ticket files
2. **Sensitive Information**: Be mindful of tickets containing sensitive data
3. **Backup**: Consider regular backups of ticket files
4. **Access Control**: Only authorized users should edit tickets

## FAQ

### How does this compare to Linear?

- Local file system vs cloud-based
- File-based vs database
- Simpler vs feature-rich
- Fast vs slow updates

### What happens to existing Linear tickets?

- Migrate them to local file system
- Update references in scripts
- Keep historical data for reference

### Is this backward compatible?

- Yes, agents use the same interface
- No breaking changes to workflow
- Just different underlying implementation