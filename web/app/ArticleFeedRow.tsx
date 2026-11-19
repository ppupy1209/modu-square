"use client";

import Link from "next/link";
import type { Article } from "../lib/community-api";
import { publishedAt } from "../lib/community-api";
import { articleWriterName } from "../lib/article-feed";
import { Icon } from "./Icon";

function shortDate(date: string) {
  const zonedDate = /(?:Z|[+-]\d{2}:\d{2})$/.test(date)
    ? date
    : `${date}+09:00`;
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    timeZone: "Asia/Seoul",
  }).format(new Date(zonedDate));
}

function compact(value = 0) {
  return new Intl.NumberFormat("ko-KR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

export function ArticleFeedRow({
  article,
  liked,
  saved,
  likeDisabled,
  onLike,
  onSave,
}: {
  article: Article;
  liked: boolean;
  saved: boolean;
  likeDisabled: boolean;
  onLike: () => void;
  onSave: () => void;
}) {
  return (
    <article className="article-row">
      <Link className="article-link" href={`/articles/${article.articleId}`}>
        <h3>
          <span className="article-topic">{article.tag ?? "일상"}</span>
          {article.title}
        </h3>
        <p className="article-excerpt">{article.content}</p>
      </Link>
      <div className="article-row-bottom">
        <div className="article-byline">
          <span title={articleWriterName(article)}>
            {articleWriterName(article)}
          </span>
          <time
            dateTime={article.createdAt}
            title={publishedAt(article.createdAt)}
          >
            {shortDate(article.createdAt)}
          </time>
        </div>
        <div className="article-row-actions">
          <button
            type="button"
            className={`article-reaction${liked ? " liked" : ""}`}
            aria-label={`${article.title} ${liked ? "좋아요 취소" : "좋아요"}, 현재 ${article.articleLikeCount ?? 0}개`}
            aria-pressed={liked}
            disabled={likeDisabled}
            onClick={onLike}
          >
            <Icon name="heart" />
            <span>{compact(article.articleLikeCount)}</span>
          </button>
          <Link
            className="article-stat"
            href={`/articles/${article.articleId}#comments-title`}
            aria-label={`${article.title} 댓글 ${article.articleCommentCount ?? 0}개`}
          >
            <Icon name="comment" />
            <span>{compact(article.articleCommentCount)}</span>
          </Link>
          <span
            className="article-stat article-views"
            aria-label={`조회 ${article.articleViewCount ?? 0}회`}
          >
            <Icon name="eye" />
            <span>{compact(article.articleViewCount)}</span>
          </span>
          <button
            type="button"
            className="save-button"
            aria-label={`${article.title} ${saved ? "저장 해제" : "저장"}`}
            aria-pressed={saved}
            onClick={onSave}
          >
            <Icon name="bookmark" />
          </button>
        </div>
      </div>
    </article>
  );
}
