"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  CommunityFooter,
  CommunityHeader,
  CommunityPage,
} from "../CommunityChrome";
import { ArticleFeedRow } from "../ArticleFeedRow";
import { Icon, topicIcon } from "../Icon";
import {
  Article,
  ArticleLikeStatus,
  popularScore,
  fetchAllArticles,
  getOrCreateGuestIdentity,
  LIKE_API,
  requestJson,
} from "../../lib/community-api";
import {
  ARTICLE_CATEGORIES,
  ARTICLE_TAGS,
  articleTopicFromSearch,
  filterArticles,
  paginateArticles,
  paginationItems,
  withTags,
} from "../../lib/article-feed";

const PAGE_SIZE = 6;

export function CommunityArticlesPage() {
  const [category, setCategory] = useState("전체");
  const [sortOrder, setSortOrder] = useState<"latest" | "popular">("latest");
  const [query, setQuery] = useState("");
  const [articles, setArticles] = useState<Article[]>([]);
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
      setCategory(articleTopicFromSearch(window.location.search));
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
    const restoreFilters = () => {
      setCategory(articleTopicFromSearch(window.location.search));
      setQuery(new URLSearchParams(window.location.search).get("q") ?? "");
      setCurrentPage(1);
    };
    window.addEventListener("popstate", restoreFilters);
    return () => {
      window.clearTimeout(restoreBrowserState);
      window.removeEventListener("popstate", restoreFilters);
    };
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

  const showDirectory = category === "전체" && !query.trim();
  const filtered = useMemo(() => {
    const items = filterArticles(articles, category, query);
    return sortOrder === "popular"
      ? [...items].sort(
          (left, right) => popularScore(right) - popularScore(left),
        )
      : items;
  }, [articles, category, query, sortOrder]);
  const page = useMemo(
    () => paginateArticles(filtered, currentPage, PAGE_SIZE),
    [filtered, currentPage],
  );
  const visibleArticleKey = showDirectory
    ? ""
    : page.items.map((article) => article.articleId).join(",");

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
    setQuery("");
    setSortOrder("latest");
    window.history.pushState(
      null,
      "",
      nextCategory === "전체"
        ? "/community"
        : `/community?topic=${encodeURIComponent(nextCategory)}`,
    );
    setCurrentPage(1);
    setNotice("");
  }

  function changeQuery(nextQuery: string) {
    setQuery(nextQuery);
    setCurrentPage(1);
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    setCategory("전체");
    window.history.pushState(
      null,
      "",
      query.trim()
        ? `/community?q=${encodeURIComponent(query.trim())}`
        : "/community",
    );
    setCurrentPage(1);
    setNotice(
      query.trim()
        ? `“${query.trim()}” 검색 결과입니다.`
        : "모든 이야기를 보여드리고 있어요.",
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
        .getElementById("community-feed")
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

    setLikedIds((current) => {
      const next = new Set(current);
      if (nextLiked) next.add(articleId);
      else next.delete(articleId);
      return next;
    });
    setBusyLikeIds((current) => new Set(current).add(articleId));
    setArticles((items) =>
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
      ),
    );

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
        active="community"
      />
      <CommunityPage className="community-browser-page">
        <header className="community-browser-intro">
          <div className="section-heading">
            <h1>
              {showDirectory
                ? "커뮤니티"
                : query.trim()
                  ? "검색 결과"
                  : `${category} 이야기`}
            </h1>
            <Link className="mobile-write" href="/write">
              <Icon name="pen" />
              글쓰기
            </Link>
          </div>
          <p>
            {showDirectory
              ? "관심 있는 이야기를 둘러보세요."
              : query.trim()
                ? `“${query.trim()}” 검색 결과입니다.`
                : `${category}에 관한 이야기를 나눠보세요.`}
          </p>
        </header>
        {notice && (
          <p className="notice" role="status">
            {notice}
          </p>
        )}
        {showDirectory ? (
          <div
            className="topic-directory"
            aria-label="주제별 커뮤니티"
            aria-busy={isLoading}
          >
            {ARTICLE_TAGS.map((topic) => {
              const topicArticles = filterArticles(articles, topic, "");
              return (
                <section className="topic-section" key={topic}>
                  <button
                    className="topic-section-heading"
                    type="button"
                    onClick={() => selectCategory(topic)}
                    aria-label={`${topic} 이야기 보기`}
                  >
                    <span className="topic-symbol">
                      <Icon name={topicIcon(topic)} />
                    </span>
                    <h2>{topic}</h2>
                    {!isLoading && (
                      <span className="topic-count">
                        {topicArticles.length.toLocaleString("ko-KR")}
                      </span>
                    )}
                    <Icon name="right" />
                  </button>
                  {isLoading ? (
                    <p className="topic-loading">이야기를 불러오고 있어요.</p>
                  ) : topicArticles.length ? (
                    <ul>
                      {topicArticles.slice(0, 3).map((article) => (
                        <li key={article.articleId}>
                          <Link href={`/articles/${article.articleId}`}>
                            <span>{article.title}</span>
                            <small
                              aria-label={`댓글 ${article.articleCommentCount ?? 0}개`}
                            >
                              {article.articleCommentCount ?? 0}
                            </small>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <div className="topic-empty">
                      <p>아직 이 주제의 이야기가 없어요.</p>
                      <Link href="/write">첫 이야기 쓰기</Link>
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        ) : (
          <section
            className="community-browser-feed"
            id="community-feed"
            aria-label="커뮤니티 글 목록"
          >
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
                  {item === "전체" ? "모든 주제" : item}
                </button>
              ))}
            </div>
            <div className="feed-toolbar">
              <span>{filtered.length.toLocaleString("ko-KR")}개의 이야기</span>
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
            <div className="article-list" aria-busy={isLoading}>
              {isLoading && (
                <div className="empty-state" role="status">
                  <strong>이야기를 불러오고 있어요.</strong>
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
                  <span>다른 주제를 살펴보거나 첫 글을 시작해보세요.</span>
                  <Link href="/write">첫 이야기 쓰기</Link>
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
        )}
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
