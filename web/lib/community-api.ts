export const ARTICLE_API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9000";
export const ARTICLE_READ_API = process.env.NEXT_PUBLIC_ARTICLE_READ_API_BASE_URL ?? "http://localhost:9005";
export const COMMENT_API = process.env.NEXT_PUBLIC_COMMENT_API_BASE_URL ?? "http://localhost:9001";
export const LIKE_API = process.env.NEXT_PUBLIC_LIKE_API_BASE_URL ?? "http://localhost:9002";
export const VIEW_API = process.env.NEXT_PUBLIC_VIEW_API_BASE_URL ?? "http://localhost:9003";
export const HOT_ARTICLE_API = process.env.NEXT_PUBLIC_HOT_ARTICLE_API_BASE_URL ?? "http://localhost:9004";
export const NOTIFICATION_API = process.env.NEXT_PUBLIC_NOTIFICATION_API_BASE_URL ?? "http://localhost:9006";
export const MEDIA_API = process.env.NEXT_PUBLIC_MEDIA_API_BASE_URL ?? "http://localhost:9007";

export const BOARD_ID = "2";
const GUEST_ID_STORAGE_KEY = "modu-square-guest-id";

export type Article = {
  articleId: string;
  title: string;
  content: string;
  writerId: string;
  writerType?: "GUEST" | "MEMBER" | null;
  writerNickname?: string | null;
  boardId?: string;
  createdAt: string;
  modifiedAt?: string;
  articleCommentCount?: number;
  articleLikeCount?: number;
  articleViewCount?: number;
  tag?: string;
};

export type Comment = {
  commentId: string;
  content: string;
  articleId: string;
  writerId: string;
  deleted: boolean;
  path?: string;
  createdAt: string;
};

export type HotArticle = {
  articleId: string;
  title: string;
  createdAt?: string;
  articleLikeCount: number;
  articleCommentCount: number;
  articleViewCount: number;
};

export type ArticlePage = {
  articles: Article[];
  articleCount: number;
};

export type ArticleLikeStatus = {
  articleId: string;
  userId: string;
  liked: boolean;
};

export type GuestIdentity = {
  userId: string;
  displayName: "Guest";
};

export type MediaStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";

export type MediaAsset = {
  mediaId: string;
  articleId?: string;
  originalFilename: string;
  contentType: string;
  originalSize: number;
  thumbnailSize?: number;
  width?: number;
  height?: number;
  status: MediaStatus;
  uploadMode: "PROXY" | "DIRECT";
  originalUrl: string;
  thumbnailUrl?: string;
  failureReason?: string;
};

export type UploadTicket = {
  mediaId: string;
  uploadUrl: string;
  headers: Record<string, string>;
  expiresAt: string;
};
export type Notification = {
  notificationId: string;
  articleId: string;
  title: string;
  commentCount: number;
  likeCount: number;
  eventCount: number;
  updatedAt: string;
};

export function normalizeArticle<T extends Pick<Article, "articleId" | "title" | "content">>(article: T): T {
  return article;
}

export function getOrCreateGuestIdentity(): GuestIdentity {
  if (typeof window === "undefined") {
    return { userId: "0", displayName: "Guest" };
  }

  const storedId = window.localStorage.getItem(GUEST_ID_STORAGE_KEY);
  if (storedId && /^\d{13,16}$/.test(storedId)) {
    return { userId: storedId, displayName: "Guest" };
  }

  const randomPart = new Uint16Array(1);
  window.crypto.getRandomValues(randomPart);
  const userId = String(Date.now() * 1000 + (randomPart[0] % 1000));
  window.localStorage.setItem(GUEST_ID_STORAGE_KEY, userId);
  return { userId, displayName: "Guest" };
}

export function toArticle(item: Article): Article {
  return {
    ...item,
    articleId: String(item.articleId),
    boardId: item.boardId == null ? undefined : String(item.boardId),
    writerId: String(item.writerId),
    articleCommentCount: item.articleCommentCount ?? 0,
    articleLikeCount: item.articleLikeCount ?? 0,
    articleViewCount: item.articleViewCount ?? 0,
  };
}

export async function fetchArticlePage(page: number, pageSize: number, signal?: AbortSignal) {
  const response = await requestJson<ArticlePage>(
    `${ARTICLE_READ_API}/v1/articles?boardId=${BOARD_ID}&page=${page}&pageSize=${pageSize}`,
    { signal }
  );
  return {
    articleCount: response.articleCount,
    articles: response.articles.map(toArticle),
  };
}

export async function fetchAllArticles(signal?: AbortSignal) {
  const pageSize = 50;
  const firstPage = await fetchArticlePage(1, pageSize, signal);
  const totalPages = Math.ceil(firstPage.articleCount / pageSize);
  if (totalPages <= 1) return firstPage.articles;

  const remainingPages = await Promise.all(
    Array.from({ length: totalPages - 1 }, (_, index) => fetchArticlePage(index + 2, pageSize, signal))
  );
  return [firstPage, ...remainingPages].flatMap((page) => page.articles);
}

type HotArticleResponse = Partial<Omit<HotArticle, "articleId">> & { articleId: string | number };

// 순위와 점수는 Hot Article Service가 정한다. 화면은 받은 순서를 그대로 보여준다.
export async function fetchPopularArticles(signal?: AbortSignal): Promise<HotArticle[]> {
  const items = await requestJson<HotArticleResponse[] | undefined>(`${HOT_ARTICLE_API}/v1/hot-articles/articles`, { signal });
  return (items ?? []).map((item) => ({
    articleId: String(item.articleId),
    title: item.title ?? "",
    createdAt: item.createdAt,
    articleLikeCount: item.articleLikeCount ?? 0,
    articleCommentCount: item.articleCommentCount ?? 0,
    articleViewCount: item.articleViewCount ?? 0,
  }));
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(`요청을 처리하지 못했습니다. (${response.status})`);
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (response.status === 204 || response.headers.get("content-length") === "0" || !contentType.includes("application/json")) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export function publishedAt(date: string) {
  const zonedDate = /(?:Z|[+-]\d{2}:\d{2})$/.test(date) ? date : `${date}+09:00`;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "Asia/Seoul",
  }).format(new Date(zonedDate));
}
