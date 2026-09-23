// 모든 k6 스크립트가 공유하는 설정.
// 실행 파라미터는 환경변수로 조정할 수 있게 하고, 기본값은 로컬 Docker 구성 기준으로 둔다.

function env(name, fallback) {
  const value = __ENV[name];
  return value === undefined || value === "" ? fallback : value;
}

function num(name, fallback) {
  const value = env(name, null);
  return value === null ? fallback : Number(value);
}

export const urls = {
  article: env("ARTICLE_URL", "http://localhost:9000"),
  comment: env("COMMENT_URL", "http://localhost:9001"),
  like: env("LIKE_URL", "http://localhost:9002"),
  view: env("VIEW_URL", "http://localhost:9003"),
  hotArticle: env("HOT_ARTICLE_URL", "http://localhost:9004"),
  articleRead: env("ARTICLE_READ_URL", "http://localhost:9005"),
  auth: env("AUTH_URL", "http://localhost:9008"),
  push: env("PUSH_URL", "http://localhost:9009"),
  pushProvider: env("PUSH_PROVIDER_URL", "http://localhost:9010"),
};

// 조회 대상 자유게시판. 15,000,100건이 들어 있다.
export const boardId = num("BOARD_ID", 1);

// 쓰기 테스트로 만들어지는 게시글은 자유게시판을 오염시키지 않도록 별도 board에 넣는다.
export const testBoardId = num("TEST_BOARD_ID", 9001);

// 댓글 쓰기 테스트 전용 article ID.
// comment_v2의 unique key가 path 단독이라 path "00000"은 전역에서 한 게시글만 가질 수 있다.
// 따라서 댓글 테스트는 이미 그 path를 점유한 이 ID 하나에만 붙인다.
export const testArticleId = num("TEST_ARTICLE_ID", 9001);

// 자유게시판 실제 게시글 수. 깊은 페이지 구간 계산에 사용한다.
export const totalArticles = num("TOTAL_ARTICLES", 15000100);

export const pageSize = num("PAGE_SIZE", 30);

// 각 실행을 구분하는 태그. 지정하지 않으면 실행 시각으로 만든다.
export const testId = env("TEST_ID", `local-${new Date().toISOString().replace(/[:.]/g, "-")}`);
export const dataset = env("DATASET", String(totalArticles));

// 쓰기 테스트가 사용할 user ID 범위. 충돌 의도를 제어하기 위해 시나리오별로 분리한다.
export const userIdRange = {
  // 좋아요 중복 방지 검증용: 좁은 범위를 의도적으로 공유해 동시 요청을 충돌시킨다.
  contended: { min: num("CONTENDED_USER_MIN", 1), max: num("CONTENDED_USER_MAX", 50) },
  // 일반 쓰기: 넓은 범위로 충돌을 피한다.
  unique: { min: num("UNIQUE_USER_MIN", 100000), max: num("UNIQUE_USER_MAX", 999999) },
};

export function envNum(name, fallback) {
  return num(name, fallback);
}

export function envStr(name, fallback) {
  return env(name, fallback);
}

// 모든 실행에 공통으로 붙는 태그. Grafana에서 실행을 구분하는 기준이 된다.
export function baseTags(testType) {
  return {
    testid: testId,
    test_type: testType,
    dataset: dataset,
  };
}

// 기본 요약에는 p99가 없어서 p95만 보이게 된다. 문서에 p99를 적어야 하므로 명시적으로 넣는다.
export const summaryTrendStats = ["avg", "min", "med", "max", "p(95)", "p(99)"];

/**
 * VU 수 상한.
 *
 * 목표 도착률에 비례해 VU를 무한정 늘리면, 응답이 느려질 때 k6가 VU를 계속 만들어내며
 * 부하 생성기 자신이 먼저 무너진다. 실제로 목표 4,000 RPS 단계에서 maxVUs가 16,000까지 늘어나
 * 로컬 머신 load average가 111까지 올라가고 Docker VM이 응답 불능이 된 적이 있다.
 * (k6 생성기와 모든 서비스가 같은 머신에서 CPU를 공유하는 구성이라 더 쉽게 발생한다.)
 *
 * VU가 이 상한에 닿으면 dropped_iterations가 늘어나며, 그것은 "시스템의 한계"가 아니라
 * "생성기가 목표 도착률을 만들지 못했다"는 신호다. 두 가지를 혼동하면 안 된다.
 */
export const maxVusCap = num("MAX_VUS_CAP", 1200);

/** 도착률 기반 시나리오의 VU 풀 크기를 상한 안에서 계산한다. */
export function vuPool(rate) {
  return {
    preAllocatedVUs: Math.min(maxVusCap, Math.max(20, Math.ceil(rate / 2))),
    maxVUs: Math.min(maxVusCap, Math.max(100, rate * 4)),
  };
}

// 초기 SLO 초안. baseline 실측 후 조정하되 결과에 맞춰 사후 왜곡하지 않는다.
export const slo = {
  listP95: num("SLO_LIST_P95", 300),
  listP99: num("SLO_LIST_P99", 800),
  deepPageP95: num("SLO_DEEP_P95", 1000),
  writeP95: num("SLO_WRITE_P95", 500),
  httpFailRate: 0.005,
  checkRate: 0.995,
};
