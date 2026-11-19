import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import ts from "../node_modules/typescript/lib/typescript.js";

async function render(path = "/") {
  const workerUrl = new URL("../dist/server/ssr/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request(`http://localhost${path}`, {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) },
    },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the Modu Square community page", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>Modu Square/);
  assert.match(html, /새로운 이야기/);
  assert.match(html, /class="home-layout"/);
  assert.match(html, /주제 둘러보기/);
  assert.doesNotMatch(html, /signal-hero|hero-highlights|quote-card/);
  assert.match(html, />Guest</);
  assert.doesNotMatch(html, /함께 만드는 광장/);
  assert.doesNotMatch(
    html,
    /깊은 페이지|독립 서비스|SYSTEM PULSE|Kafka|Redis|Grafana|15M\+/i,
  );
  assert.doesNotMatch(
    html,
    /codex-preview|Your site is taking shape|react-loading-skeleton/,
  );
});

test("server-renders writing, article detail, community, popular, and auth routes", async () => {
  const [
    writeResponse,
    detailResponse,
    communityResponse,
    popularResponse,
    authResponse,
  ] = await Promise.all([
    render("/write"),
    render("/articles/8000000000000000099"),
    render("/community"),
    render("/popular"),
    render("/auth"),
  ]);

  assert.equal(writeResponse.status, 200);
  assert.match(
    await writeResponse.text(),
    /<title>새 글 쓰기 — Modu Square<\/title>/,
  );
  assert.equal(detailResponse.status, 200);
  assert.match(
    await detailResponse.text(),
    /<title>이야기 — Modu Square<\/title>/,
  );
  assert.equal(communityResponse.status, 200);
  const communityHtml = await communityResponse.text();
  assert.match(communityHtml, /<title>커뮤니티 — Modu Square<\/title>/);
  assert.match(communityHtml, /일상, 취향, 질문, 동네, 여행, 정보/);
  assert.equal(popularResponse.status, 200);
  assert.match(
    await popularResponse.text(),
    /<title>인기글 — Modu Square<\/title>/,
  );
  assert.equal(authResponse.status, 200);
  const authHtml = await authResponse.text();
  assert.match(authHtml, /<title>로그인 · 회원가입 — Modu Square<\/title>/);
});

test("paginates each topic from its own filtered articles", async () => {
  const source = await readFile(
    new URL("../lib/article-feed.ts", import.meta.url),
    "utf8",
  );
  const javascript = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ES2022,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
  const feed = await import(
    `data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`
  );
  const makeArticle = (
    articleId,
    title,
    content = "오늘 있었던 일을 기록합니다",
  ) => ({
    articleId: String(articleId),
    writerId: String(articleId),
    title,
    content,
    createdAt: "2026-08-26T00:00:00",
  });
  const articles = feed.withTags([
    ...Array.from({ length: 8 }, (_, index) =>
      makeArticle(index + 1, `평범한 하루 ${index + 1}`),
    ),
    ...Array.from({ length: 3 }, (_, index) =>
      makeArticle(index + 20, `주말 여행 ${index + 1}`),
    ),
    ...Array.from({ length: 2 }, (_, index) =>
      makeArticle(index + 30, `좋아하는 음악 ${index + 1}`),
    ),
  ]);

  const allPage = feed.paginateArticles(
    feed.filterArticles(articles, "전체", ""),
    1,
    6,
  );
  assert.equal(allPage.totalPages, 3);
  assert.equal(allPage.items.length, 6);

  const travelArticles = feed.filterArticles(articles, "여행", "");
  const travelPage = feed.paginateArticles(travelArticles, 1, 6);
  assert.equal(travelPage.totalPages, 1);
  assert.equal(travelPage.items.length, 3);

  const invalidTravelPage = feed.paginateArticles(travelArticles, 2, 6);
  assert.equal(invalidTravelPage.currentPage, 1);
  assert.equal(invalidTravelPage.items.length, 3);

  assert.equal(feed.ARTICLE_TAGS.length, 6);
  const infoArticle = makeArticle(
    70,
    "도서관 이용 안내",
    "운영 시간을 공유합니다",
  );
  assert.equal(feed.articleTag(infoArticle), "정보");
  assert.equal(feed.articleTag(makeArticle(71, "신청 기간 안내")), "정보");
  assert.equal(
    feed.articleTag(
      makeArticle(72, "오늘 하루", "동료 덕분에 회의 정리가 잘됐어요"),
    ),
    "일상",
  );
  assert.equal(feed.articleTag(makeArticle(73, "주말 여행")), "여행");
  assert.deepEqual(
    feed.filterArticles(feed.withTags([infoArticle, ...articles]), "정보", ""),
    [{ ...infoArticle, tag: "정보" }],
  );
  assert.equal(
    feed.articleTopicFromSearch("?topic=" + encodeURIComponent("정보")),
    "정보",
  );
  assert.equal(feed.articleTopicFromSearch("?topic=unknown"), "전체");
  assert.equal(feed.articleTopicFromSearch("?q=책"), "전체");

  const legacyArticle = makeArticle(90, "기존 글");
  const memberArticle = {
    ...makeArticle(91, "회원 글"),
    writerId: "42",
    writerType: "MEMBER",
    writerNickname: "연우",
  };
  assert.equal(feed.articleWriterName(legacyArticle), "modu_90");
  assert.equal(feed.articleWriterName(memberArticle), "연우");
  assert.deepEqual(
    feed.filterArticles(
      feed.withTags([legacyArticle, memberArticle]),
      "전체",
      "연우",
    ),
    [{ ...memberArticle, tag: "일상" }],
  );
});

test("keeps accessibility, navigation, authentication, and social preview contracts", async () => {
  const [
    page,
    community,
    chrome,
    detail,
    write,
    authPage,
    authApi,
    articleFeed,
    layout,
    css,
    packageJson,
    smallSeed,
    writerMigration,
    compose,
    communityData,
    likeConsistency,
    largeSeed,
    row,
  ] = await Promise.all([
    readFile(new URL("../app/ModuSquareApp.tsx", import.meta.url), "utf8"),
    readFile(
      new URL("../app/community/CommunityArticlesPage.tsx", import.meta.url),
      "utf8",
    ),
    readFile(new URL("../app/CommunityChrome.tsx", import.meta.url), "utf8"),
    readFile(
      new URL(
        "../app/articles/[articleId]/ArticleDetailPage.tsx",
        import.meta.url,
      ),
      "utf8",
    ),
    readFile(
      new URL("../app/write/WriteArticlePage.tsx", import.meta.url),
      "utf8",
    ),
    readFile(new URL("../app/auth/AuthPage.tsx", import.meta.url), "utf8"),
    readFile(new URL("../lib/auth-api.ts", import.meta.url), "utf8"),
    readFile(new URL("../lib/article-feed.ts", import.meta.url), "utf8"),
    readFile(new URL("../app/layout.tsx", import.meta.url), "utf8"),
    readFile(new URL("../app/globals.css", import.meta.url), "utf8"),
    readFile(new URL("../package.json", import.meta.url), "utf8"),
    readFile(
      new URL("../../infra/mysql/init/01-schema.sql", import.meta.url),
      "utf8",
    ),
    readFile(
      new URL(
        "../../infra/mysql/migrations/08-article-writer.sql",
        import.meta.url,
      ),
      "utf8",
    ),
    readFile(new URL("../../docker-compose.yml", import.meta.url), "utf8"),
    readFile(
      new URL(
        "../../infra/mysql/migrations/03-community-data.sql",
        import.meta.url,
      ),
      "utf8",
    ),
    readFile(
      new URL(
        "../../infra/mysql/migrations/06-community-like-consistency.sql",
        import.meta.url,
      ),
      "utf8",
    ),
    readFile(
      new URL("../../infra/mysql/seed/seed-articles.sh", import.meta.url),
      "utf8",
    ),
    readFile(new URL("../app/ArticleFeedRow.tsx", import.meta.url), "utf8"),
  ]);

  assert.match(chrome, /aria-label="게시글 검색"/);
  assert.match(chrome, /href="\/write"/);
  assert.match(chrome, /href="\/community"/);
  assert.match(
    chrome,
    /className="main-nav"[\s\S]*href="\/popular"[\s\S]*>인기글[\s\S]*href="\/community"/,
  );
  assert.match(chrome, /getOrCreateGuestIdentity/);
  assert.match(chrome, />Guest</);
  assert.match(chrome, /restoreAuthMember/);
  assert.match(chrome, /logoutMember/);
  assert.match(chrome, /로그인 · 회원가입/);
  assert.doesNotMatch(chrome, /YW|\/#community/);
  assert.match(authPage, /registerMember/);
  assert.match(authPage, /loginMember/);
  assert.match(authPage, /window\.location\.replace\("\/"\)/);
  assert.doesNotMatch(authPage, /useRouter/);
  assert.match(authPage, /minLength=\{mode === "register" \? 10 : undefined\}/);
  assert.match(authApi, /credentials: "include"/);
  assert.match(authApi, /window\.sessionStorage/);
  assert.match(authApi, /SESSION_HINT_STORAGE_KEY/);
  assert.match(authApi, /\/v1\/auth\/refresh/);
  assert.match(authApi, /restoreSessionPromise/);
  assert.match(authApi, /getAuthAccessToken/);
  assert.match(page, /href=\{`\/articles\/\$\{article\.articleId\}`\}/);
  assert.match(page, /popularArticles\.map/);
  assert.match(page, /fetchAllArticles/);
  assert.match(page, /paginateArticles/);
  assert.match(page, /setCurrentPage\(1\)/);
  assert.match(page, /toggleArticleLike/);
  assert.match(page, /liked=\{likedIds\.has\(article\.articleId\)\}/);
  assert.match(row, /aria-pressed=\{liked\}/);
  assert.match(row, /aria-pressed=\{saved\}/);
  assert.match(page, /optimistic-lock/);
  assert.match(page, /className="pagination"/);
  assert.doesNotMatch(page, /demoArticles|loadMore|함께 만드는 광장/);
  assert.match(page, /인기글 전체 보기/);
  assert.match(community, /ARTICLE_CATEGORIES\.map/);
  assert.match(community, /filterArticles/);
  assert.match(community, /paginateArticles/);
  assert.match(community, /toggleArticleLike/);
  assert.match(community, /active="community"/);
  assert.match(community, /관심 있는 이야기를 둘러보세요/);
  assert.match(community, /articleTopicFromSearch/);
  assert.match(community, /window\.addEventListener\("popstate"/);
  assert.match(detail, /aria-pressed=\{liked\}/);
  assert.match(detail, /이야기를 불러오고 있어요/);
  assert.match(detail, /articleWriterName/);
  assert.match(write, /<form className="editor-form"/);
  assert.match(write, /어떤 이야기를 나누고 싶나요/);
  assert.match(write, /getAuthAccessToken/);
  assert.match(write, /Authorization: `Bearer \$\{accessToken\}`/);
  assert.match(articleFeed, /article\.writerType === "MEMBER"/);
  assert.match(articleFeed, /`modu_\$\{article\.writerId\}`/);
  assert.match(page, /role="status"/);
  assert.match(layout, /\/og\.png/);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
  assert.match(css, /prefers-reduced-transparency:\s*reduce/);
  assert.match(css, /prefers-contrast:\s*more/);
  assert.doesNotMatch(packageJson, /react-loading-skeleton/);
  assert.doesNotMatch(smallSeed, /topic=|sequence=|THEN '[^']* #'/);
  assert.doesNotMatch(smallSeed, /8000000000000000/);
  assert.match(smallSeed, /article\.article_writer/);
  assert.match(writerMigration, /article\.article_writer/);
  assert.match(writerMigration, /FOREIGN KEY \(article_id\)/i);
  assert.doesNotMatch(writerMigration, /ALTER\s+TABLE\s+article\.article/i);
  assert.doesNotMatch(writerMigration, /UPDATE\s+article\.article/i);
  assert.match(compose, /08-article-writer\.sql/);
  assert.match(communityData, /article_like\.article_like/);
  assert.match(communityData, /comment\.comment_v2/);
  assert.match(communityData, /article_view\.article_view_count/);
  assert.match(
    communityData,
    /SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5\) tens/,
  );
  assert.match(likeConsistency, /SELECT 51 n UNION ALL SELECT 52/);
  assert.match(likeConsistency, /COUNT\(likes\.article_like_id\)/);
  assert.doesNotMatch(largeSeed, /topic=|sequence=|THEN '[^']* #'/);
});
