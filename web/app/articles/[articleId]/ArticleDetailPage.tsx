"use client";

import { Icon } from "../../Icon";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  CommunityFooter,
  CommunityHeader,
  CommunityPage,
} from "../../CommunityChrome";
import {
  ARTICLE_API,
  ARTICLE_READ_API,
  COMMENT_API,
  LIKE_API,
  MEDIA_API,
  VIEW_API,
  Article,
  ArticleLikeStatus,
  Comment,
  getOrCreateGuestIdentity,
  MediaAsset,
  normalizeArticle,
  publishedAt,
  requestJson,
} from "../../../lib/community-api";
import { articleTag, articleWriterName } from "../../../lib/article-feed";
import { restoreAuthMember } from "../../../lib/auth-api";

type CommentPageResponse = { comments: Comment[]; commentCount: number };

export function ArticleDetailPage() {
  const params = useParams<{ articleId: string }>();
  const router = useRouter();
  const articleId = params.articleId;
  const [article, setArticle] = useState<Article | null>(null);
  const [media, setMedia] = useState<MediaAsset[]>([]);
  const [comments, setComments] = useState<Comment[]>([]);
  const [commentCount, setCommentCount] = useState(0);
  const [likeCount, setLikeCount] = useState(0);
  const [viewCount, setViewCount] = useState(0);
  const [liked, setLiked] = useState(false);
  const [comment, setComment] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isWorking, setIsWorking] = useState(false);
  const [guestUserId, setGuestUserId] = useState("");
  const [memberId, setMemberId] = useState<number | null>(null);

  useEffect(() => {
    const restoreGuest = window.setTimeout(() => {
      setGuestUserId(getOrCreateGuestIdentity().userId);
    }, 0);
    return () => window.clearTimeout(restoreGuest);
  }, []);

  useEffect(() => {
    let active = true;
    restoreAuthMember().then((member) => {
      if (active) setMemberId(member?.memberId ?? null);
    });
    return () => {
      active = false;
    };
  }, []);

  const loadComments = useCallback(async () => {
    const data = await requestJson<CommentPageResponse>(
      `${COMMENT_API}/v2/comments?articleId=${articleId}&page=1&pageSize=50`,
    );
    setComments(
      data.comments.map((item) => ({
        ...item,
        commentId: String(item.commentId),
        articleId: String(item.articleId),
        writerId: String(item.writerId),
      })),
    );
    setCommentCount(data.commentCount);
  }, [articleId]);

  useEffect(() => {
    if (!guestUserId) return;
    let cancelled = false;
    async function load() {
      setIsLoading(true);
      try {
        const articleData = await requestJson<Article>(
          `${ARTICLE_READ_API}/v1/articles/${articleId}`,
        );
        if (cancelled) return;
        setArticle(
          normalizeArticle({
            ...articleData,
            articleId: String(articleData.articleId),
            writerId: String(articleData.writerId),
          }),
        );
        setCommentCount(articleData.articleCommentCount ?? 0);
        setLikeCount(articleData.articleLikeCount ?? 0);
        setViewCount(articleData.articleViewCount ?? 0);

        const [commentsResult, viewResult, likedResult] =
          await Promise.allSettled([
            loadComments(),
            requestJson<number>(
              `${VIEW_API}/v1/article-views/articles/${articleId}/users/${guestUserId}`,
              { method: "POST" },
            ),
            requestJson<ArticleLikeStatus>(
              `${LIKE_API}/v1/article-likes/articles/${articleId}/users/${guestUserId}`,
            ),
          ]);
        if (cancelled) return;
        if (viewResult.status === "fulfilled") setViewCount(viewResult.value);
        if (likedResult.status === "fulfilled")
          setLiked(likedResult.value.liked);
        if (commentsResult.status === "rejected") setComments([]);
      } catch (cause) {
        if (!cancelled)
          setError(
            cause instanceof Error
              ? cause.message
              : "게시글을 불러오지 못했습니다.",
          );
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [articleId, guestUserId, loadComments]);

  useEffect(() => {
    let cancelled = false;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;

    async function refreshMedia() {
      try {
        const items = await requestJson<MediaAsset[]>(
          `${MEDIA_API}/v1/media/articles/${articleId}`,
        );
        if (cancelled) return;
        setMedia(items);
        if (
          items.some(
            (item) => item.status === "PENDING" || item.status === "PROCESSING",
          )
        ) {
          refreshTimer = setTimeout(refreshMedia, 800);
        }
      } catch {
        if (!cancelled) setMedia([]);
      }
    }

    refreshMedia();
    return () => {
      cancelled = true;
      if (refreshTimer) clearTimeout(refreshTimer);
    };
  }, [articleId]);

  async function toggleLike() {
    if (isWorking || !guestUserId) return;
    const nextLiked = !liked;
    setLiked(nextLiked);
    setLikeCount((count) => Math.max(0, count + (nextLiked ? 1 : -1)));
    setIsWorking(true);
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
      setLiked(!nextLiked);
      setLikeCount((count) => Math.max(0, count + (nextLiked ? -1 : 1)));
      setNotice(
        cause instanceof Error
          ? cause.message
          : "좋아요를 반영하지 못했습니다.",
      );
    } finally {
      setIsWorking(false);
    }
  }

  async function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = comment.trim();
    if (content.length < 2) {
      setNotice("댓글을 두 글자 이상 입력해 주세요.");
      return;
    }
    if (!guestUserId) return;
    setIsWorking(true);
    try {
      await requestJson<Comment>(`${COMMENT_API}/v2/comments`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ articleId, content, writerId: guestUserId }),
      });
      setComment("");
      await loadComments();
      setNotice("댓글을 등록했어요.");
    } catch (cause) {
      setNotice(
        cause instanceof Error ? cause.message : "댓글을 등록하지 못했습니다.",
      );
    } finally {
      setIsWorking(false);
    }
  }

  async function deleteComment(commentId: string) {
    setIsWorking(true);
    try {
      await requestJson<void>(`${COMMENT_API}/v2/comments/${commentId}`, {
        method: "DELETE",
      });
      await loadComments();
      setNotice("댓글을 삭제했어요.");
    } catch (cause) {
      setNotice(
        cause instanceof Error ? cause.message : "댓글을 삭제하지 못했습니다.",
      );
    } finally {
      setIsWorking(false);
    }
  }

  async function deleteArticle() {
    if (
      !window.confirm("이 이야기를 삭제할까요? 삭제 후에는 되돌릴 수 없습니다.")
    )
      return;
    setIsWorking(true);
    try {
      await requestJson<void>(`${ARTICLE_API}/v1/articles/${articleId}`, {
        method: "DELETE",
      });
      router.push("/");
    } catch (cause) {
      setNotice(
        cause instanceof Error
          ? cause.message
          : "게시글을 삭제하지 못했습니다.",
      );
      setIsWorking(false);
    }
  }

  return (
    <div className="site-shell">
      <CommunityHeader />
      <CommunityPage className="detail-page">
        <div className="page-breadcrumb">
          <Link href="/">
            <Icon name="arrow" />
            새로운 이야기
          </Link>
          <span>{article ? articleTag(article) : "이야기"}</span>
        </div>
        {isLoading && (
          <div className="page-state" role="status">
            <strong>이야기를 불러오고 있어요.</strong>
            <span>잠시만 기다려 주세요.</span>
          </div>
        )}
        {!isLoading && error && (
          <div className="page-state error-state" role="alert">
            <strong>이야기를 열 수 없어요.</strong>
            <span>{error}</span>
            <Link href="/">홈으로 돌아가기</Link>
          </div>
        )}
        {!isLoading && article && (
          <>
            <article className="detail-card">
              <div className="detail-meta">
                <span>{article ? articleTag(article) : "이야기"}</span>
                <span>{articleWriterName(article)}</span>
                <span>·</span>
                <time>{publishedAt(article.createdAt)}</time>
              </div>
              <h1>{article.title}</h1>
              <p className="detail-content">{article.content}</p>
              {media.length > 0 && (
                <div
                  className="detail-media-gallery"
                  aria-label="게시글 첨부 이미지"
                >
                  {media.map((item) => (
                    <figure key={item.mediaId} data-status={item.status}>
                      {item.status === "READY" && item.thumbnailUrl ? (
                        <a
                          href={item.originalUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          <img
                            src={item.thumbnailUrl}
                            alt={item.originalFilename}
                            width={item.width}
                            height={item.height}
                            loading="lazy"
                          />
                        </a>
                      ) : item.status === "FAILED" ? (
                        <div className="media-processing media-failed">
                          <strong>이미지를 처리하지 못했습니다.</strong>
                          <span>잠시 후 다시 시도해 주세요.</span>
                        </div>
                      ) : (
                        <div className="media-processing" role="status">
                          <span
                            className="media-processing-dot"
                            aria-hidden="true"
                          />
                          <strong>이미지를 보기 좋게 준비하고 있어요.</strong>
                        </div>
                      )}
                    </figure>
                  ))}
                </div>
              )}
              <div className="detail-actions">
                <button
                  className={`reaction-button ${liked ? "liked" : ""}`}
                  type="button"
                  onClick={toggleLike}
                  disabled={isWorking}
                  aria-pressed={liked}
                >
                  <Icon name="heart" /> 좋아요{" "}
                  {likeCount.toLocaleString("ko-KR")}
                </button>
                <span>
                  <Icon name="comment" />
                  댓글 {commentCount.toLocaleString("ko-KR")}
                </span>
                <span>
                  <Icon name="eye" />
                  읽음 {viewCount.toLocaleString("ko-KR")}
                </span>
                {(article.writerId === guestUserId ||
                  (article.writerType === "MEMBER" &&
                    article.writerId === String(memberId))) && (
                  <button
                    className="delete-link"
                    type="button"
                    onClick={deleteArticle}
                    disabled={isWorking}
                  >
                    글 삭제
                  </button>
                )}
              </div>
            </article>

            <section className="comments-card" aria-labelledby="comments-title">
              <div className="comments-heading">
                <div>
                  <p className="section-kicker">CONVERSATION</p>
                  <h2 id="comments-title">댓글 {commentCount}</h2>
                </div>
              </div>
              <form className="comment-form" onSubmit={submitComment}>
                <span
                  className="comment-avatar guest-avatar"
                  aria-hidden="true"
                >
                  G
                </span>
                <label>
                  <span className="sr-only">댓글 내용</span>
                  <textarea
                    value={comment}
                    onChange={(event) => setComment(event.target.value)}
                    rows={3}
                    maxLength={1000}
                    placeholder="이야기에 따뜻한 댓글을 남겨보세요"
                  />
                </label>
                <button type="submit" disabled={isWorking || !comment.trim()}>
                  댓글 등록
                </button>
              </form>
              {notice && (
                <p className="notice" role="status">
                  {notice}
                </p>
              )}
              <div className="comment-list">
                {comments.map((item) => (
                  <article className="comment-item" key={item.commentId}>
                    <span className="comment-avatar" aria-hidden="true">
                      {item.writerId === guestUserId
                        ? "G"
                        : `M${item.writerId.slice(-2)}`}
                    </span>
                    <div>
                      <div className="comment-meta">
                        <strong>modu_{item.writerId}</strong>
                        <time>{publishedAt(item.createdAt)}</time>
                      </div>
                      <p>
                        {item.deleted ? "삭제된 댓글입니다." : item.content}
                      </p>
                    </div>
                    {!item.deleted && item.writerId === guestUserId && (
                      <button
                        type="button"
                        onClick={() => deleteComment(item.commentId)}
                        disabled={isWorking}
                      >
                        삭제
                      </button>
                    )}
                  </article>
                ))}
                {!comments.length && (
                  <div className="empty-comments">
                    <strong>첫 댓글을 기다리고 있어요.</strong>
                    <span>이 이야기에 가장 먼저 마음을 건네보세요.</span>
                  </div>
                )}
              </div>
            </section>
          </>
        )}
      </CommunityPage>
      <CommunityFooter />
    </div>
  );
}
