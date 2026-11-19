"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { CommunityFooter, CommunityHeader } from "./CommunityChrome";
import { ArticleFeedRow } from "./ArticleFeedRow";
import { TopicNavigation } from "./TopicNavigation";
import { Icon } from "./Icon";
import {
  Article,
  ArticleLikeStatus,
  HotArticle,
  fetchAllArticles,
  fetchPopularArticles,
  getOrCreateGuestIdentity,
  LIKE_API,
  requestJson,
} from "../lib/community-api";
import {
  ARTICLE_CATEGORIES,
  filterArticles,
  rankedArticles,
  paginateArticles,
  paginationItems,
  withTags,
} from "../lib/article-feed";

const PAGE_SIZE = 6;

function formatCompact(value = 0) {
  return new Intl.NumberFormat("ko-KR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function ModuSquareApp() {
  const [category, setCategory] = useState("전체");
  const [sortOrder, setSortOrder] = useState<"latest" | "popular">("latest");
  const [query, setQuery] = useState("");
  const [articles, setArticles] = useState<Article[]>([]);
  const [popularArticles, setPopularArticles] = useState<HotArticle[]>([]);
  const [popularLoading, setPopularLoading] = useState(true);
  const [popularError, setPopularError] = useState(false);
  const [notice, setNotice] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set());
  const [guestUserId, setGuestUserId] = useState("");
  const [likedIds, setLikedIds] = useState<Set<string>>(new Set());
  const [checkedLikeIds, setCheckedLikeIds] = useState<Set<string>>(new Set());
  const [busyLikeIds, setBusyLikeIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    const restoreBrowserState = window.setTimeout(() => {
      const requestedQuery = new URLSearchParams(window.location.search).get(
        "q",
      );
      if (requestedQuery) setQuery(requestedQuery);
      setGuestUserId(getOrCreateGuestIdentity().userId);
      try {
        setSavedIds(
          new Set(
            JSON.parse(
              localStorage.getItem("modu-square-saved") ?? "[]",
            ) as string[],
          ),
        );
      } catch {
        localStorage.removeItem("modu-square-saved");
      }
    }, 0);
    return () => window.clearTimeout(restoreBrowserState);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchAllArticles(controller.signal)
      .then((items) => setArticles(withTags(items)))
      .catch((error: Error) => {
        if (error.name !== "AbortError") {
          setArticles([]);
          setNotice("이야기를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchPopularArticles(controller.signal)
      .then((items) => setPopularArticles(items.slice(0, 5)))
      .catch((error: Error) => {
        if (error.name !== "AbortError") {
          setPopularArticles([]);
          setPopularError(true);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setPopularLoading(false);
      });
    return () => controller.abort();
  }, []);

  const filtered = useMemo(() => {
    const source =
      sortOrder === "popular"
        ? rankedArticles(articles, popularArticles)
        : articles;
    return filterArticles(source, category, query);
  }, [articles, popularArticles, category, query, sortOrder]);

  const page = useMemo(
    () => paginateArticles(filtered, currentPage, PAGE_SIZE),
    [filtered, currentPage],
  );
  const visibleArticleKey = page.items
    .map((article) => article.articleId)
    .join(",");

  useEffect(() => {
    if (!guestUserId || !visibleArticleKey) return;
    let cancelled = false;
    const articleIds = visibleArticleKey.split(",");

    Promise.all(
      articleIds.map(async (articleId) => {
        try {
          const status = await requestJson<ArticleLikeStatus>(
            `${LIKE_API}/v1/article-likes/articles/${articleId}/users/${guestUserId}`,
          );
          return { articleId, liked: status.liked };
        } catch {
          return null;
        }
      }),
    ).then((statuses) => {
      if (cancelled) return;
      setLikedIds((current) => {
        const next = new Set(current);
        statuses.forEach((status) => {
          if (!status) return;
          if (status.liked) next.add(status.articleId);
          else next.delete(status.articleId);
        });
        return next;
      });
      setCheckedLikeIds((current) => {
        const next = new Set(current);
        statuses.forEach((status) => {
          if (status) next.add(status.articleId);
        });
        return next;
      });
    });

    return () => {
      cancelled = true;
    };
  }, [guestUserId, visibleArticleKey]);

  function selectCategory(nextCategory: string) {
    setCategory(nextCategory);
    setCurrentPage(1);
    setNotice("");
  }

  function changeQuery(nextQuery: string) {
    setQuery(nextQuery);
    setCurrentPage(1);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    if (query.trim()) setCategory("전체");
    setCurrentPage(1);
    setNotice(
      query.trim()
        ? `“${query.trim()}” 검색 결과입니다.`
        : "새로운 이야기부터 보여드리고 있어요.",
    );
  }

  function goToPage(nextPage: number) {
    if (
      nextPage === page.currentPage ||
      nextPage < 1 ||
      nextPage > page.totalPages
    )
      return;
    setNotice("");
    setCurrentPage(nextPage);
    window.requestAnimationFrame(() =>
      document
        .getElementById("feed")
        ?.scrollIntoView({ behavior: "smooth", block: "start" }),
    );
  }

  function toggleSaved(articleId: string) {
    setSavedIds((current) => {
      const next = new Set(current);
      if (next.has(articleId)) {
        next.delete(articleId);
        setNotice("저장한 글에서 제외했어요.");
      } else {
        next.add(articleId);
        setNotice("나중에 읽을 글에 저장했어요.");
      }
      localStorage.setItem("modu-square-saved", JSON.stringify([...next]));
      return next;
    });
  }

  async function toggleArticleLike(articleId: string) {
    if (
      !guestUserId ||
      !checkedLikeIds.has(articleId) ||
      busyLikeIds.has(articleId)
    )
      return;
    const nextLiked = !likedIds.has(articleId);
    const delta = nextLiked ? 1 : -1;
    const updateLikeCount = <
      T extends Pick<Article, "articleId" | "articleLikeCount">,
    >(
      items: T[],
    ) =>
      items.map((article) =>
        article.articleId === articleId
          ? {
              ...article,
              articleLikeCount: Math.max(
                0,
                (article.articleLikeCount ?? 0) + delta,
              ),
            }
          : article,
      );

    setLikedIds((current) => {
      const next = new Set(current);
      if (nextLiked) next.add(articleId);
      else next.delete(articleId);
      return next;
    });
    setBusyLikeIds((current) => new Set(current).add(articleId));
    setArticles(updateLikeCount);
    setPopularArticles(updateLikeCount);

    try {
      await requestJson<void>(
        `${LIKE_API}/v1/article-likes/articles/${articleId}/users/${guestUserId}/optimistic-lock`,
        {
          method: nextLiked ? "POST" : "DELETE",
        },
      );
      setNotice(
        nextLiked ? "이 이야기를 좋아합니다." : "좋아요를 취소했습니다.",
      );
    } catch (cause) {
      setLikedIds((current) => {
        const next = new Set(current);
        if (nextLiked) next.delete(articleId);
        else next.add(articleId);
        return next;
      });
      setArticles((items) =>
        items.map((article) =>
          article.articleId === articleId
            ? {
                ...article,
                articleLikeCount: Math.max(
                  0,
                  (article.articleLikeCount ?? 0) - delta,
                ),
              }
            : article,
        ),
      );
      setPopularArticles((items) =>
        items.map((article) =>
          article.articleId === articleId
            ? {
                ...article,
                articleLikeCount: Math.max(
                  0,
                  (article.articleLikeCount ?? 0) - delta,
                ),
              }
            : article,
        ),
      );
      setNotice(
        cause instanceof Error
          ? cause.message
          : "좋아요를 반영하지 못했습니다.",
      );
    } finally {
      setBusyLikeIds((current) => {
        const next = new Set(current);
        next.delete(articleId);
        return next;
      });
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader
        query={query}
        onQueryChange={changeQuery}
        onSearchSubmit={submitSearch}
        active="home"
      />
      <main id="main-content" className="home-layout">
        <header className="feed-heading">
          <div className="section-heading">
            <h1>새로운 이야기</h1>
            <Link className="mobile-write" href="/write">
              <Icon name="pen" />
              글쓰기
            </Link>
          </div>
          <div
            className="category-tabs"
            role="tablist"
            aria-label="게시글 카테고리"
          >
            {ARTICLE_CATEGORIES.map((item) => (
              <button
                key={item}
                type="button"
                role="tab"
                aria-selected={category === item}
                onClick={() => selectCategory(item)}
              >
                {item}
              </button>
            ))}
          </div>
          <div className="feed-toolbar">
            <span>
              {query.trim()
                ? `“${query.trim()}” 검색 결과`
                : `${category} 이야기`}
            </span>
            <div className="feed-sort" aria-label="글 정렬">
              <button
                type="button"
                aria-pressed={sortOrder === "latest"}
                onClick={() => {
                  setSortOrder("latest");
                  setCurrentPage(1);
                }}
              >
                최신순
              </button>
              <button
                type="button"
                aria-pressed={sortOrder === "popular"}
                onClick={() => {
                  setSortOrder("popular");
                  setCurrentPage(1);
                }}
              >
                인기순
              </button>
            </div>
          </div>
        </header>
        <section
          className="feed-column"
          id="feed"
          aria-label="새로운 이야기 목록"
        >
          {notice && (
            <p className="notice" role="status">
              {notice}
            </p>
          )}
          <div className="article-list" aria-busy={isLoading}>
            {isLoading && (
              <div className="empty-state" role="status">
                <strong>새로운 이야기를 불러오고 있어요.</strong>
                <span>잠시만 기다려 주세요.</span>
              </div>
            )}
            {!isLoading &&
              page.items.map((article) => (
                <ArticleFeedRow
                  key={article.articleId}
                  article={article}
                  liked={likedIds.has(article.articleId)}
                  saved={savedIds.has(article.articleId)}
                  likeDisabled={
                    !guestUserId ||
                    !checkedLikeIds.has(article.articleId) ||
                    busyLikeIds.has(article.articleId)
                  }
                  onLike={() => toggleArticleLike(article.articleId)}
                  onSave={() => toggleSaved(article.articleId)}
                />
              ))}
            {!isLoading && !filtered.length && (
              <div className="empty-state">
                <strong>아직 이 주제의 이야기가 없어요.</strong>
                <span>다른 키워드로 찾아보거나 첫 글을 시작해보세요.</span>
              </div>
            )}
          </div>
          <nav className="pagination" aria-label="게시글 페이지">
            <button
              type="button"
              onClick={() => goToPage(page.currentPage - 1)}
              disabled={page.currentPage === 1}
              aria-label="이전 페이지"
            >
              <Icon name="left" />
            </button>
            {paginationItems(page.currentPage, page.totalPages).map(
              (item, index) =>
                item === "gap" ? (
                  <span className="pagination-gap" key={`gap-${index}`}>
                    ...
                  </span>
                ) : (
                  <button
                    key={item}
                    type="button"
                    className={item === page.currentPage ? "active" : ""}
                    aria-current={
                      item === page.currentPage ? "page" : undefined
                    }
                    onClick={() => goToPage(item)}
                  >
                    {item}
                  </button>
                ),
            )}
            <button
              type="button"
              onClick={() => goToPage(page.currentPage + 1)}
              disabled={page.currentPage === page.totalPages}
              aria-label="다음 페이지"
            >
              <Icon name="right" />
            </button>
          </nav>
        </section>
        <aside className="side-column" aria-label="인기글과 주제">
          <section className="popular-summary" aria-labelledby="popular-title">
            <div className="side-title">
              <h2 id="popular-title">오늘의 인기글</h2>
              <Link href="/popular" aria-label="인기글 전체 보기">
                전체 보기
              </Link>
            </div>
            <ol className="popular-list">
              {popularArticles.map((article, index) => (
                <li key={article.articleId}>
                  <strong className="popular-rank">{index + 1}</strong>
                  <Link href={`/articles/${article.articleId}`}>
                    <span>{article.title}</span>
                    <small>
                      좋아요 {formatCompact(article.articleLikeCount)}개, 댓글{" "}
                      {formatCompact(article.articleCommentCount)}개
                    </small>
                  </Link>
                </li>
              ))}
            </ol>
            {!popularArticles.length && (
              <p className="popular-empty">
                {popularLoading
                  ? "인기글을 확인하고 있어요."
                  : popularError
                    ? "인기글을 불러오지 못했어요."
                    : "아직 집계된 인기글이 없어요."}
              </p>
            )}
          </section>
          <TopicNavigation />
        </aside>
      </main>
      <CommunityFooter />
    </div>
  );
}
