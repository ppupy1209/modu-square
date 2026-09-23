#!/usr/bin/env bash
# 공지 발송 도중 Push Service가 강제 종료되는 상황을 재현한다.
#   1. 운영자가 같은 Idempotency-Key로 발송을 두 번 요청한다 (k6).
#   2. 업체 전달이 KILL_AFTER건을 넘으면 Push Service를 SIGKILL로 종료한다.
#      커밋하지 못한 offset의 메시지가 재시작 뒤 다시 전달된다.
#   3. RESTART_DELAY초 뒤 다시 띄우고 발송이 끝날 때까지 기다린다.
#
# 실행 예시:
#   TEST_ID=push-crash-20260923 ./load-tests/push-broadcast-crash.sh
set -euo pipefail

KILL_AFTER="${KILL_AFTER:-3000}"
RESTART_DELAY="${RESTART_DELAY:-5}"
export MSYS_NO_PATHCONV=1

delivered() {
  curl -s localhost:9010/metrics | awk '/^push_provider_delivered_total/ {print $2}'
}

echo "start $(date +%s)"
TEST_TYPE=push-broadcast PUSH_MODE=async docker compose run --rm --no-deps -e PUSH_WATCH_SECONDS=600 \
  k6 run /scripts/push-broadcast.js > "load-tests/results/push-broadcast-${TEST_ID}.log" 2>&1 &
k6_pid=$!

sleep 3
until [ "$(delivered)" -ge "${KILL_AFTER}" ]; do sleep 0.2; done
docker kill --signal=KILL modu-square-push-1 > /dev/null
echo "killed $(date +%s) delivered=$(delivered)"

sleep "${RESTART_DELAY}"
docker compose up -d --no-deps push > /dev/null 2>&1
echo "restarted $(date +%s)"

wait "${k6_pid}"
echo "end $(date +%s)"
grep -E "submit|COMPLETED" "load-tests/results/push-broadcast-${TEST_ID}.log" | tail -2
