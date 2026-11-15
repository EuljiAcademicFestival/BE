#!/usr/bin/env bash
set -euo pipefail

task_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$task_script_dir/.."
if ! command -v k6 >/dev/null 2>&1; then
  echo 'k6가 없습니다. 먼저 brew install k6를 실행하세요.' >&2
  exit 1
fi
mkdir -p k6/results
exec k6 run "$@" k6/festival.js
