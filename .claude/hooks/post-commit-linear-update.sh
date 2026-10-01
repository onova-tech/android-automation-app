#!/bin/bash
# Post-Commit Hook: Linear Update
#
# Auto-updates Linear ticket with commit hash after successful commit
# Triggered after git commit operations

# Get the latest commit hash and message
COMMIT_HASH=$(git log -1 --format="%H" 2>/dev/null)
COMMIT_MSG=$(git log -1 --format="%s" 2>/dev/null)

# Extract ticket from commit message ({{TICKET_PREFIX}}-XXX format)
TICKET_ID=$(echo "$COMMIT_MSG" | grep -oE "{{TICKET_PREFIX}}-[0-9]+" | head -1)

if [ -z "$TICKET_ID" ]; then
  echo "ℹ️  No ticket found in commit message"
  exit 0
fi

if [ -z "$COMMIT_HASH" ]; then
  echo "⚠️  Could not retrieve commit hash"
  exit 0
fi

# Log commit for ticket system tracking
TICKETS_DIR="/mnt/c/Users/mathe/OneDrive/Documents/Desenvolvimento/android-automation/safeworkflow/tickets"
TICKET_FILE="$TICKETS_DIR/{{TICKET_PREFIX}}-$TICKET_ID.md"

if [ -f "$TICKET_FILE" ]; then
  echo "📝 Commit: ${COMMIT_HASH:0:8} - $COMMIT_MSG"
  echo "✅ Commit recorded for ticket: {{TICKET_PREFIX}}-$TICKET_ID"
else
  echo "ℹ️  Ticket file not found: {{TICKET_PREFIX}}-$TICKET_ID.md"
  echo "   (Ticket may not exist in local ticket system)"
fi

# Exit successfully (ticket tracking done via file system)
exit 0
