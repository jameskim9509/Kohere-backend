#!/usr/bin/env bash
set -euo pipefail

echo "Backend base repository setup"

if [ ! -f "CLAUDE.local.md" ]; then
  cp CLAUDE.local.example.md CLAUDE.local.md
  echo "Created CLAUDE.local.md"
fi

echo "Done"
