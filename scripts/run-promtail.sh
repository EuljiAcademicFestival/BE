#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -x "$1" ]]; then
  echo '사용법: bash scripts/run-promtail.sh /절대경로/promtail-darwin-arm64' >&2
  echo '실행 파일 경로와 실행 권한을 확인하세요.' >&2
  exit 1
fi

task_binary_dir="$(cd "$(dirname "$1")" && pwd)"
task_binary="$task_binary_dir/$(basename "$1")"
task_script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$task_script_dir/.."
mkdir -p logs
exec "$task_binary" --config.file="$PWD/monitoring/promtail/promtail-config.yml"
