#!/bin/sh
# infrastructure/kafka/init-topics.sh 에 토픽을 추가/수정한 뒤 이 스크립트로 반영한다.
#
# kafka-topics-init은 "한 번 실행하고 끝나는" job이라, 이미 Exited 상태면 docker compose up -d 만으로는
# 다시 실행되지 않는다. --force-recreate로 강제로 재실행하고, --no-deps로 kafka(브로커)는 건드리지
# 않는다 — 브로커 재시작도, 다운타임도 없다. init-topics.sh는 --if-not-exists를 쓰므로 기존 토픽은
# 그대로 두고 새로 추가된 것만 생성된다.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"

docker compose -f "$COMPOSE_FILE" up -d --force-recreate --no-deps kafka-topics-init
docker logs --tail 30 earlybird-kafka-topics-init