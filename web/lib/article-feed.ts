import type { Article, HotArticle } from "./community-api";

export const ARTICLE_TAGS = [
  "일상",
  "취향",
  "질문",
  "동네",
  "여행",
  "정보",
] as const;
export const ARTICLE_CATEGORIES = ["전체", ...ARTICLE_TAGS] as const;

export function articleTag(article: Article) {
  if (
    /정보\s*(공유|정리)|이용\s*안내|신청\s*(방법|기간)|모집\s*(공고|안내)|지원금|운영\s*시간|휴무\s*안내|할인\s*(정보|소식)|혜택\s*(안내|정리)/.test(
      article.title,
    )
  )
    return "정보";
  const text = `${article.title} ${article.content}`;
  if (/여행|산책|동네|가게|공원/.test(text))
    return /여행/.test(text) ? "여행" : "동네";
  if (/추천|책|영화|음악|앨범|취미|사진/.test(text)) return "취향";
  if (/궁금|어떻게|무엇|있나요|알려/.test(text)) return "질문";
  return "일상";
}

export function withTags(articles: Article[]) {
  return articles.map((article) => ({ ...article, tag: articleTag(article) }));
}

export function articleWriterName(article: Article) {
  const nickname = article.writerNickname?.trim();
  return article.writerType === "MEMBER" && nickname
    ? nickname
    : `modu_${article.writerId}`;
}

export function filterArticles(
  articles: Article[],
  category: string,
  query: string,
) {
  const normalized = query.trim().toLowerCase();
  return articles.filter((article) => {
    const inCategory = category === "전체" || article.tag === category;
    const inQuery =
      !normalized ||
      `${article.title} ${article.content} ${articleWriterName(article)} modu_${article.writerId}`
        .toLowerCase()
        .includes(normalized);
    return inCategory && inQuery;
  });
}

export function paginateArticles<T>(
  items: T[],
  requestedPage: number,
  pageSize: number,
) {
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(Math.max(1, requestedPage), totalPages);
  const offset = (currentPage - 1) * pageSize;
  return {
    currentPage,
    totalPages,
    items: items.slice(offset, offset + pageSize),
  };
}

export function paginationItems(currentPage: number, totalPages: number) {
  const pages = new Set([
    1,
    totalPages,
    currentPage - 1,
    currentPage,
    currentPage + 1,
  ]);
  const visible = [...pages]
    .filter((page) => page >= 1 && page <= totalPages)
    .sort((a, b) => a - b);
  const items: Array<number | "gap"> = [];
  visible.forEach((page, index) => {
    if (index > 0 && page - visible[index - 1] > 1) items.push("gap");
    items.push(page);
  });
  return items;
}

export function articleTopicFromSearch(search: string): string {
  const topic = new URLSearchParams(search).get("topic");
  return ARTICLE_TAGS.find((item) => item === topic) ?? "전체";
}

// Keep the order supplied by Hot Article Service; only join the local board's article details.
export function rankedArticles(
  articles: Article[],
  ranking: Pick<HotArticle, "articleId">[],
): Article[] {
  const byId = new Map(articles.map((article) => [article.articleId, article]));
  const seen = new Set<string>();
  return ranking.flatMap((item) => {
    const article = byId.get(item.articleId);
    if (!article || seen.has(item.articleId)) return [];
    seen.add(item.articleId);
    return [article];
  });
}
