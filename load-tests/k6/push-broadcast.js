// 운영자가 공지 발송을 요청하는 상황을 재현한다.
// 발송 부하 자체는 Push Service가 외부 업체로 보내는 호출이고, k6는 운영자의 행동만 흉내 낸다.
//
// PUSH_MODE=sync  : 응답이 게이트웨이 타임아웃(PUSH_CLIENT_TIMEOUT)을 넘기면 운영자가 5초 뒤 한 번 더 누른다.
// PUSH_MODE=async : 같은 Idempotency-Key로 두 번 누른 뒤(더블 클릭, 응답 유실 후 재전송),
//                   발송 현황을 5초마다 조회한다.
//
// 실행 예시:
// TEST_ID=push-sync PUSH_MODE=sync \
// docker compose run --rm k6 run /scripts/push-broadcast.js

import http from "k6/http";
import { check, sleep } from "k6";
import { urls, baseTags, summaryTrendStats, envStr, envNum, testId } from "./config.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "push-broadcast";
const MODE = envStr("PUSH_MODE", "async");
const CLIENT_TIMEOUT = envStr("PUSH_CLIENT_TIMEOUT", "60s");
const WATCH_SECONDS = envNum("PUSH_WATCH_SECONDS", 360);
const TITLE = envStr("PUSH_TITLE", `서비스 점검 안내 ${testId}`);
const BODY = "9월 25일 새벽 2시부터 30분 동안 서비스 점검이 진행됩니다.";

export const options = {
  scenarios: {
    operator: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: "15m",
    },
  },
  tags: baseTags(TEST_TYPE),
  summaryTrendStats,
};

export function setup() {
  // 실행마다 업체 대역의 수신 기록을 비워 중복 수를 0부터 센다.
  http.post(`${urls.pushProvider}/reset`);
}

export default function () {
  if (MODE === "sync") {
    submitUntilResponded();
  } else {
    submitTwiceAndWatch();
  }
}

function submitUntilResponded() {
  const payload = JSON.stringify({ title: TITLE, body: BODY });
  for (let attempt = 1; attempt <= 2; attempt++) {
    const res = http.post(`${urls.push}/v1/push/broadcasts`, payload, {
      headers: { "Content-Type": "application/json" },
      timeout: CLIENT_TIMEOUT,
      tags: { name: "push_broadcast_submit", attempt: String(attempt) },
    });
    console.log(`submit attempt=${attempt} status=${res.status} duration=${Math.round(res.timings.duration)}ms`);
    if (res.status === 200) {
      return;
    }
    sleep(5);
  }
}

function submitTwiceAndWatch() {
  const payload = JSON.stringify({ title: TITLE, body: BODY });
  const params = {
    headers: { "Content-Type": "application/json", "Idempotency-Key": `broadcast-${testId}` },
    timeout: CLIENT_TIMEOUT,
    tags: { name: "push_broadcast_submit" },
  };

  const first = http.post(`${urls.push}/v1/push/broadcasts`, payload, params);
  const second = http.post(`${urls.push}/v1/push/broadcasts`, payload, params);
  const firstId = first.json("broadcastId");
  console.log(`submit first=${first.status}(${Math.round(first.timings.duration)}ms) second=${second.status}(${Math.round(second.timings.duration)}ms) id=${firstId}`);
  check(null, {
    "첫 요청 접수": () => first.status === 202,
    "같은 키의 두 번째 요청은 같은 발송을 돌려준다": () => second.json("broadcastId") === firstId,
  });

  const deadline = Date.now() + WATCH_SECONDS * 1000;
  while (Date.now() < deadline) {
    sleep(5);
    const res = http.get(`${urls.push}/v1/push/broadcasts/${firstId}`, {
      tags: { name: "push_broadcast_status" },
    });
    // Push Service가 재시작하는 동안에는 조회가 실패한다. 발송은 서버에서 계속되므로 다음 조회를 기다린다.
    if (res.status !== 200) {
      console.log(`status unavailable (${res.status})`);
      continue;
    }
    console.log(`status ${res.body}`);
    if (res.json("status") === "COMPLETED") {
      return;
    }
  }
}

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
