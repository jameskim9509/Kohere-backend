#!/usr/bin/env bash
set -euo pipefail

INPUT="$(cat || true)"
FILE_PATH="$(printf '%s' "$INPUT" | python -c 'import sys,json; data=json.load(sys.stdin); print(data.get("tool_input",{}).get("file_path",""))' 2>/dev/null || true)"

if [ -n "$FILE_PATH" ]; then
  echo "[format-after-edit] Edited: $FILE_PATH"
fi

exit 0
