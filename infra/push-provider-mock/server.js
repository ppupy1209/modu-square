// 외부 푸시 발송 업체 대역 서버.
// 실제 업체처럼 응답 지연과 실패를 돌려주고, 같은 기기가 같은 공지를 두 번 받았는지 센다.
// 중복은 막지 않고 세기만 한다. 중복을 막는 책임은 호출하는 Push Service에 있다.
const http = require("node:http");

const PORT = Number(process.env.PORT ?? 8080);
const LATENCY_MS = Number(process.env.LATENCY_MS ?? 30);
const LATENCY_JITTER_MS = Number(process.env.LATENCY_JITTER_MS ?? 10);
const TRANSIENT_FAILURE_RATE = Number(process.env.TRANSIENT_FAILURE_RATE ?? 0.03);

let requests = new Map(); // HTTP 상태별 응답 수
let delivered = new Set(); // 받은 적 있는 (토큰, 공지 제목)
let deliveredTotal = 0;
let duplicateTotal = 0;

function count(status) {
  requests.set(status, (requests.get(status) ?? 0) + 1);
}

function reply(res, status, body) {
  count(status);
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}

function handleSend(message, res) {
  const { token, title } = message;
  if (!token || !title) {
    return reply(res, 400, { code: "INVALID_REQUEST" });
  }
  if (token.startsWith("expired-")) {
    return reply(res, 410, { code: "UNREGISTERED" });
  }
  if (token.startsWith("stuck-") || Math.random() < TRANSIENT_FAILURE_RATE) {
    return reply(res, 503, { code: "UNAVAILABLE" });
  }

  const key = `${token}|${title}`;
  deliveredTotal += 1;
  if (delivered.has(key)) {
    duplicateTotal += 1;
  } else {
    delivered.add(key);
  }
  return reply(res, 200, { messageId: `${Date.now()}-${deliveredTotal}` });
}

function metrics() {
  const lines = [
    "# HELP push_provider_requests_total 업체가 받은 발송 요청 수",
    "# TYPE push_provider_requests_total counter",
    ...[...requests].map(([status, value]) => `push_provider_requests_total{status="${status}"} ${value}`),
    "# HELP push_provider_delivered_total 업체가 기기로 전달한 푸시 수",
    "# TYPE push_provider_delivered_total counter",
    `push_provider_delivered_total ${deliveredTotal}`,
    "# HELP push_provider_unique_delivered 한 번 이상 받은 (기기, 공지) 수",
    "# TYPE push_provider_unique_delivered gauge",
    `push_provider_unique_delivered ${delivered.size}`,
    "# HELP push_provider_duplicate_delivered_total 같은 기기가 같은 공지를 다시 받은 수",
    "# TYPE push_provider_duplicate_delivered_total counter",
    `push_provider_duplicate_delivered_total ${duplicateTotal}`,
  ];
  return lines.join("\n") + "\n";
}

http
  .createServer((req, res) => {
    if (req.method === "GET" && req.url === "/metrics") {
      res.writeHead(200, { "Content-Type": "text/plain; version=0.0.4" });
      return res.end(metrics());
    }
    if (req.method === "POST" && req.url === "/reset") {
      requests = new Map();
      delivered = new Set();
      deliveredTotal = 0;
      duplicateTotal = 0;
      res.writeHead(204);
      return res.end();
    }
    if (req.method === "POST" && req.url === "/v1/messages") {
      let raw = "";
      req.on("data", (chunk) => (raw += chunk));
      req.on("end", () => {
        const delay = LATENCY_MS + (Math.random() * 2 - 1) * LATENCY_JITTER_MS;
        setTimeout(() => {
          try {
            handleSend(JSON.parse(raw), res);
          } catch {
            reply(res, 400, { code: "INVALID_JSON" });
          }
        }, Math.max(0, delay));
      });
      return;
    }
    res.writeHead(404);
    res.end();
  })
  .listen(PORT, () => console.log(`push provider mock listening on ${PORT}`));
