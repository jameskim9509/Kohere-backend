#!/usr/bin/env bash
set -euo pipefail

echo "[pre-pr-check] Start"
bash scripts/quality/check.sh
git status --short || true
echo "[pre-pr-check] Done"
