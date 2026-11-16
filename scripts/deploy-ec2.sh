#!/usr/bin/env bash
set -euo pipefail

digest=${1:?Expected an ECR image digest}
[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
registry=548813916405.dkr.ecr.ap-northeast-2.amazonaws.com
image="$registry/eulji-festival-backend@$digest"
cd /opt/eulji-festival
umask 077
exec 9>.deploy.lock
flock -w 30 9

compose() {
    docker compose -f compose.yml "$@"
}

check_health() {
    curl --fail --silent --show-error --max-time 5 \
        http://127.0.0.1:8080/actuator/health |
        python3 -c 'import json, sys; sys.exit(json.load(sys.stdin).get("status") != "UP")'
}

deploy() {
    compose up -d --no-deps --pull never --wait --wait-timeout 150 backend &&
        check_health
}

previous_image=$(compose config --images backend)
if [[ "$previous_image" == "$image" ]] && check_health; then
    echo "Image already healthy: $digest"
    exit 0
fi

docker image inspect "$previous_image" >/dev/null
work_dir=$(mktemp -d .deploy.XXXXXX)
rollback=false
cleanup() {
    local status=$?
    if [[ "$rollback" == true ]]; then
        echo "Deploy failed. Restoring $previous_image." >&2
        if mv -f "$work_dir/previous.yml" compose.yml && deploy; then
            echo 'Previous image is healthy.'
        else
            echo 'Rollback failed. Check the app EC2.' >&2
        fi
    fi
    rm -rf "$work_dir"
    return "$status"
}
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

docker_config="$PWD/$work_dir/docker"
mkdir "$docker_config"
aws ecr get-login-password --region ap-northeast-2 |
    docker --config "$docker_config" login --username AWS --password-stdin "$registry" >/dev/null
timeout 120 docker --config "$docker_config" pull "$image"

cp -p compose.yml "$work_dir/previous.yml"
cp -p compose.yml "$work_dir/next.yml"
python3 - "$work_dir/next.yml" "$previous_image" "$image" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
previous, image = sys.argv[2:]
pattern = r"^( +image: +)" + re.escape(previous) + r"[ \t]*$"
updated, count = re.subn(pattern, lambda match: match[1] + image, path.read_text(), flags=re.M)
if count != 1:
    sys.exit("Expected exactly one backend image entry in compose.yml")
path.write_text(updated)
PY
docker compose --project-directory "$PWD" -f "$work_dir/next.yml" config --quiet
rollback=true
mv -f "$work_dir/next.yml" compose.yml
deploy
rollback=false
echo "Deployed and healthy: $digest"
