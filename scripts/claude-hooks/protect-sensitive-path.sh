#!/usr/bin/env bash
set -euo pipefail

INPUT="$(cat || true)"
FILE_PATH="$(printf '%s' "$INPUT" | python -c 'import sys,json; data=json.load(sys.stdin); print(data.get("tool_input",{}).get("file_path",""))' 2>/dev/null || true)"

if [ -z "$FILE_PATH" ]; then
  exit 0
fi

case "$FILE_PATH" in
  .env|.env.*|*/.env|*/.env.*|secrets/*|*/secrets/*|credentials/*|*/credentials/*|*private*|*secret*)
    echo "Blocked sensitive path access: $FILE_PATH" >&2
    exit 2
    ;;
esac

exit 0
