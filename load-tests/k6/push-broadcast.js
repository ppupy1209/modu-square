// 운영자가 공지 발송을 요청하는 상황을 재현한다.
// 발송 부하 자체는 Push Service가 외부 업체로 보내는 호출이고, k6는 운영자의 행동만 흉내 낸다.
//
// 응답이 게이트웨이 타임아웃(PUSH_CLIENT_TIMEOUT)을 넘기면 운영자가 5초 뒤 한 번 더 누른다.
//
// 실행 예시:
// TEST_ID=push-sync \
// docker compose run --rm k6 run /scripts/push-broadcast.js

import http from "k6/http";
import { sleep } from "k6";
import { urls, baseTags, summaryTrendStats, envStr, testId } from "./config.js";
import { summarize } from "./helpers/summary.js";

const TEST_TYPE = "push-broadcast";
const CLIENT_TIMEOUT = envStr("PUSH_CLIENT_TIMEOUT", "60s");
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
  submitUntilResponded();
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

export function handleSummary(data) {
  return summarize(TEST_TYPE, data);
}
