#!/bin/bash

# Local Ticket System - Replaces Linear MCP
# Usage: ./local-ticket-access.sh [action] [ticket-id]

TICKETS_DIR="/mnt/c/Users/mathe/OneDrive/Documents/Desenvolvimento/android-automation/safeworkflow/tickets"

# Get ticket by ID

if [ ! -d "$TICKETS_DIR" ]; then
  echo "❌ TICKETS_DIR not found: $TICKETS_DIR"
  exit 1
fi

if [ "$(basename "$TICKETS_DIR")" != "tickets" ]; then
  echo "❌ expected tickets directory in safeworkflow, got: $TICKETS_DIR"
  exit 1
fi

# Search for ticket file

TICKET_FILE="$TICKETS_DIR/{{TICKET_PREFIX}}-$1.md"

if [ ! -f "$TICKET_FILE" ]; then
  echo "❌ Ticket not found: {{TICKET_PREFIX}}-$1.md"
  echo "Available tickets:"
  ls -1 "$TICKETS_DIR" | head -10
  exit 1
fi

cat "$TICKET_FILE"
