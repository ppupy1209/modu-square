package board.hotarticle.service;

import board.hotarticle.client.ArticleReadClient;
import board.hotarticle.repository.ArticleCommentCountRepository;
import board.hotarticle.repository.ArticleCreatedTimeRepository;
import board.hotarticle.repository.ArticleLikeCountRepository;
import board.hotarticle.repository.ArticleViewCountRepository;
import board.hotarticle.repository.HotArticleListRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import board.hotarticle.utils.TimeCalculatorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 원본 데이터로 오늘의 인기글 읽기 모델을 다시 채운다.
 *
 * <p>인기글 목록은 Kafka 이벤트로만 갱신되므로 SQL로 넣은 초기 데이터나 Redis 유실 뒤에는 비어 있다.
 * 게시글 조회 서비스의 목록에서 오늘 글의 작성 시각과 반응 수를 가져와 이벤트 경로와 같은 Redis 키에 쓴다.
 *
 * <ul>
 *     <li>점수는 {@link HotArticleScoreCalculator}로만 계산한다. 가중치를 한곳에서만 정의하기 위해서다.</li>
 *     <li>모든 값은 set-if-absent로 쓴다. 이벤트가 먼저 쓴 값이 있으면 그 값이 이긴다.</li>
 *     <li>0인 반응 수는 쓰지 않는다. 게시글 조회 서비스는 하위 서비스가 아직 뜨지 않았을 때 0을 돌려주는데,
 *     그 0이 남으면 다음 재시도가 실제 값을 쓰지 못한다. 키가 없으면 어차피 0으로 읽힌다.</li>
 *     <li>같은 입력으로 여러 번 실행해도 결과가 같다. 목록 추가는 같은 점수로 덮어쓰는 ZADD다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HotArticleRebuilder {
    private final ArticleReadClient articleReadClient;
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository;
    private final ArticleLikeCountRepository articleLikeCountRepository;
    private final ArticleCommentCountRepository articleCommentCountRepository;
    private final ArticleViewCountRepository articleViewCountRepository;
    private final HotArticleQueryModelRepository hotArticleQueryModelRepository;
    private final HotArticleScoreCalculator hotArticleScoreCalculator;
    private final HotArticleListRepository hotArticleListRepository;

    /**
     * 게시판마다 최신 글부터 최대 {@code maxPages}쪽까지만 훑는다.
     *
     * @return 인기글 목록에 다시 넣은 글 수
     */
    public int rebuild(List<Long> boardIds, long pageSize, long maxPages) {
        int rebuilt = 0;
        for (Long boardId : boardIds) {
            for (long page = 1; page <= maxPages; page++) {
                List<ArticleReadClient.ArticleReadResponse> articles = articleReadClient.readAll(boardId, page, pageSize);
                for (ArticleReadClient.ArticleReadResponse article : articles) {
                    if (rebuild(article)) {
                        rebuilt++;
                    }
                }
                if (articles.size() < pageSize) {
                    break;
                }
            }
        }
        return rebuilt;
    }

    boolean rebuild(ArticleReadClient.ArticleReadResponse article) {
        LocalDateTime createdAt = article.getCreatedAt();
        if (article.getArticleId() == null || !TimeCalculatorUtils.isActiveHotArticleDate(createdAt)) {
            return false;
        }

        Long articleId = article.getArticleId();
        Duration ttl = TimeCalculatorUtils.calculateDurationToExpiration(createdAt);
        articleCreatedTimeRepository.createIfAbsent(articleId, createdAt, ttl);
        long likeCount = valueOrZero(article.getArticleLikeCount());
        long commentCount = valueOrZero(article.getArticleCommentCount());
        long viewCount = valueOrZero(article.getArticleViewCount());
        if (likeCount > 0) {
            articleLikeCountRepository.createIfAbsent(articleId, likeCount, ttl);
        }
        if (commentCount > 0) {
            articleCommentCountRepository.createIfAbsent(articleId, commentCount, ttl);
        }
        if (viewCount > 0) {
            articleViewCountRepository.createIfAbsent(articleId, viewCount, ttl);
        }
        hotArticleQueryModelRepository.createIfAbsent(HotArticleQueryModel.create(article), ttl);

        long score = hotArticleScoreCalculator.calculate(articleId);
        // 이벤트 경로도 반응(좋아요, 댓글, 조회)이 생긴 글만 목록에 넣는다.
        if (score <= 0) {
            return false;
        }
        hotArticleListRepository.add(articleId, createdAt, score, HotArticleScoreUpdater.HOT_ARTICLE_COUNT, ttl);
        return true;
    }

    private long valueOrZero(Long count) {
        return count == null ? 0L : count;
    }
}
