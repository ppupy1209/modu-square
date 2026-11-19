package board.hotarticle.service;

import board.hotarticle.client.ArticleReadClient;
import board.hotarticle.repository.ArticleCommentCountRepository;
import board.hotarticle.repository.ArticleCreatedTimeRepository;
import board.hotarticle.repository.ArticleLikeCountRepository;
import board.hotarticle.repository.ArticleViewCountRepository;
import board.hotarticle.repository.HotArticleListRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HotArticleRebuilderTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @InjectMocks
    HotArticleRebuilder hotArticleRebuilder;
    @Mock
    ArticleReadClient articleReadClient;
    @Mock
    ArticleCreatedTimeRepository articleCreatedTimeRepository;
    @Mock
    ArticleLikeCountRepository articleLikeCountRepository;
    @Mock
    ArticleCommentCountRepository articleCommentCountRepository;
    @Mock
    ArticleViewCountRepository articleViewCountRepository;
    @Mock
    HotArticleQueryModelRepository hotArticleQueryModelRepository;
    @Mock
    HotArticleScoreCalculator hotArticleScoreCalculator;
    @Mock
    HotArticleListRepository hotArticleListRepository;

    @Test
    void rebuildsTodaysArticleWithoutOverwritingEventValues() {
        LocalDateTime createdAt = LocalDateTime.now(BUSINESS_ZONE);
        ArticleReadClient.ArticleReadResponse article = article(1L, createdAt, 5L, 3L, 20L);
        given(articleReadClient.readAll(2L, 1, 100)).willReturn(List.of(article));
        given(hotArticleScoreCalculator.calculate(1L)).willReturn(41L);

        int rebuilt = hotArticleRebuilder.rebuild(List.of(2L), 100, 10);

        assertThat(rebuilt).isEqualTo(1);
        verify(articleCreatedTimeRepository).createIfAbsent(eq(1L), eq(createdAt), any(Duration.class));
        verify(articleLikeCountRepository).createIfAbsent(eq(1L), eq(5L), any(Duration.class));
        verify(articleCommentCountRepository).createIfAbsent(eq(1L), eq(3L), any(Duration.class));
        verify(articleViewCountRepository).createIfAbsent(eq(1L), eq(20L), any(Duration.class));
        verify(hotArticleQueryModelRepository).createIfAbsent(any(HotArticleQueryModel.class), any(Duration.class));
        verify(hotArticleListRepository).add(eq(1L), eq(createdAt), eq(41L), eq(10L), any(Duration.class));

        // 이벤트가 쓴 값을 덮어쓰는 경로는 쓰지 않는다.
        verify(articleCreatedTimeRepository, never()).createOrUpdate(anyLong(), any(), any());
        verify(articleLikeCountRepository, never()).createOrUpdate(anyLong(), anyLong(), any());
        verify(articleCommentCountRepository, never()).createOrUpdate(anyLong(), anyLong(), any());
        verify(articleViewCountRepository, never()).createOrUpdate(anyLong(), anyLong(), any());
        verify(hotArticleQueryModelRepository, never()).createOrUpdate(any(), any());
    }

    @Test
    void runningTwiceWritesTheSameRanking() {
        LocalDateTime createdAt = LocalDateTime.now(BUSINESS_ZONE);
        ArticleReadClient.ArticleReadResponse article = article(1L, createdAt, 1L, 0L, 0L);
        given(articleReadClient.readAll(2L, 1, 100)).willReturn(List.of(article));
        given(hotArticleScoreCalculator.calculate(1L)).willReturn(3L);

        hotArticleRebuilder.rebuild(List.of(2L), 100, 10);
        hotArticleRebuilder.rebuild(List.of(2L), 100, 10);

        verify(hotArticleListRepository, times(2)).add(eq(1L), eq(createdAt), eq(3L), eq(10L), any(Duration.class));
        verify(articleLikeCountRepository, times(2)).createIfAbsent(eq(1L), eq(1L), any(Duration.class));
    }

    @Test
    void skipsArticlesOutsideTodaysRanking() {
        LocalDateTime twoDaysAgo = LocalDateTime.now(BUSINESS_ZONE).minusDays(2);
        ArticleReadClient.ArticleReadResponse article = article(1L, twoDaysAgo, 5L, 3L, 20L);
        given(articleReadClient.readAll(2L, 1, 100)).willReturn(List.of(article));

        int rebuilt = hotArticleRebuilder.rebuild(List.of(2L), 100, 10);

        assertThat(rebuilt).isZero();
        verifyNoInteractions(articleCreatedTimeRepository, articleLikeCountRepository, hotArticleListRepository);
    }

    @Test
    void doesNotListArticlesWithoutReactions() {
        LocalDateTime createdAt = LocalDateTime.now(BUSINESS_ZONE);
        ArticleReadClient.ArticleReadResponse article = article(1L, createdAt, 0L, 0L, 0L);
        given(articleReadClient.readAll(2L, 1, 100)).willReturn(List.of(article));
        given(hotArticleScoreCalculator.calculate(1L)).willReturn(0L);

        int rebuilt = hotArticleRebuilder.rebuild(List.of(2L), 100, 10);

        assertThat(rebuilt).isZero();
        verify(hotArticleListRepository, never()).add(anyLong(), any(), anyLong(), anyLong(), any());
        // 0을 남기면 하위 서비스가 늦게 뜬 경우 다음 재시도가 실제 값을 쓰지 못한다.
        verify(articleLikeCountRepository, never()).createIfAbsent(anyLong(), anyLong(), any());
        verify(articleCommentCountRepository, never()).createIfAbsent(anyLong(), anyLong(), any());
        verify(articleViewCountRepository, never()).createIfAbsent(anyLong(), anyLong(), any());
    }

    @Test
    void stopsAtTheLastPartialPage() {
        LocalDateTime createdAt = LocalDateTime.now(BUSINESS_ZONE);
        List<ArticleReadClient.ArticleReadResponse> firstPage = List.of(
                article(1L, createdAt, 1L, 0L, 0L),
                article(2L, createdAt, 1L, 0L, 0L)
        );
        List<ArticleReadClient.ArticleReadResponse> lastPage = List.of(article(3L, createdAt, 1L, 0L, 0L));
        given(articleReadClient.readAll(2L, 1, 2)).willReturn(firstPage);
        given(articleReadClient.readAll(2L, 2, 2)).willReturn(lastPage);
        given(hotArticleScoreCalculator.calculate(anyLong())).willReturn(3L);

        int rebuilt = hotArticleRebuilder.rebuild(List.of(2L), 2, 10);

        assertThat(rebuilt).isEqualTo(3);
        verify(articleReadClient, never()).readAll(2L, 3, 2);
    }

    @Test
    void scansAtMostMaxPages() {
        LocalDateTime createdAt = LocalDateTime.now(BUSINESS_ZONE);
        List<ArticleReadClient.ArticleReadResponse> fullPage = new ArrayList<>();
        fullPage.add(article(1L, createdAt, 1L, 0L, 0L));
        given(articleReadClient.readAll(eq(2L), anyLong(), eq(1L))).willReturn(fullPage);
        given(hotArticleScoreCalculator.calculate(1L)).willReturn(3L);

        hotArticleRebuilder.rebuild(List.of(2L), 1, 3);

        verify(articleReadClient, times(3)).readAll(eq(2L), anyLong(), eq(1L));
    }

    private ArticleReadClient.ArticleReadResponse article(
            Long articleId, LocalDateTime createdAt, Long likeCount, Long commentCount, Long viewCount
    ) {
        ArticleReadClient.ArticleReadResponse article = mock(ArticleReadClient.ArticleReadResponse.class);
        lenient().when(article.getArticleId()).thenReturn(articleId);
        lenient().when(article.getTitle()).thenReturn("title-" + articleId);
        lenient().when(article.getCreatedAt()).thenReturn(createdAt);
        lenient().when(article.getArticleLikeCount()).thenReturn(likeCount);
        lenient().when(article.getArticleCommentCount()).thenReturn(commentCount);
        lenient().when(article.getArticleViewCount()).thenReturn(viewCount);
        return article;
    }
}
