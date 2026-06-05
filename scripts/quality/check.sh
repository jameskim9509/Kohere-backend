#!/usr/bin/env bash
set -euo pipefail

echo "[quality] Running base checks..."

test -f README.md
test -f CLAUDE.md
test -f docs/index.md
test -f .claude/settings.json

echo "[quality] Checking markdown files..."
find . -name "*.md" -not -path "./.git/*" -print | sort > /tmp/backend-template-md-files.txt

echo "[quality] OK"
