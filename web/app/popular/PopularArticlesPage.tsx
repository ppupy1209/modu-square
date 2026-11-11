"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { CommunityFooter, CommunityHeader, CommunityPage } from "../CommunityChrome";
import { HotArticle, fetchPopularArticles } from "../../lib/community-api";

export function PopularArticlesPage() {
  const [articles, setArticles] = useState<HotArticle[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    fetchPopularArticles(controller.signal)
      .then((items) => setArticles(items))
      .catch((error: Error) => {
        if (error.name !== "AbortError") setHasError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  return (
    <div className="site-shell">
      <CommunityHeader active="popular" />
      <CommunityPage>
        <div className="page-breadcrumb"><Link href="/">← 홈으로</Link><span>오늘의 인기글</span></div>
        <section className="side-card popular-card popular-page-card">
          <div className="side-title">
            <div><p>TRENDING NOW</p><h1>오늘의 인기글 Top 10</h1></div>
            <span className="live-dot">NOW</span>
          </div>
          {isLoading && <div className="page-state"><strong>인기글을 불러오고 있어요.</strong></div>}
          {hasError && <div className="page-state"><strong>인기글을 불러오지 못했어요.</strong><span>잠시 후 다시 시도해 주세요.</span></div>}
          {!isLoading && !hasError && !articles.length && <div className="page-state"><strong>아직 집계된 인기글이 없어요.</strong></div>}
          {articles.length > 0 && (
            <ol>
              {articles.map((article, index) => (
                <li key={article.articleId}>
                  <strong>{String(index + 1).padStart(2, "0")}</strong>
                  <Link href={`/articles/${article.articleId}`}>
                    <span>{article.title}</span>
                    <small>좋아요 {(article.articleLikeCount ?? 0).toLocaleString("ko-KR")}개, 대화 {(article.articleCommentCount ?? 0).toLocaleString("ko-KR")}개 <b>→</b></small>
                  </Link>
                </li>
              ))}
            </ol>
          )}
        </section>
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
