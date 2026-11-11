package board.hotarticle.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

class HotArticleStartupRebuildRunnerTest {

    @Test
    void retriesUntilArticleReadServiceAnswers() {
        HotArticleRebuilder rebuilder = mock(HotArticleRebuilder.class);
        given(rebuilder.rebuild(List.of(2L), 100L, 10L))
                .willThrow(new IllegalStateException("article-read not ready"))
                .willReturn(4);
        HotArticleStartupRebuildRunner runner = new HotArticleStartupRebuildRunner(
                rebuilder, List.of(2L), 100, 10, 3, 0
        );

        boolean rebuilt = runner.rebuildWithRetry();

        assertThat(rebuilt).isTrue();
        verify(rebuilder, times(2)).rebuild(List.of(2L), 100L, 10L);
    }

    @Test
    void retriesWhenAPassFindsNothing() {
        HotArticleRebuilder rebuilder = mock(HotArticleRebuilder.class);
        // 게시글 조회 서비스가 하위 서비스 장애를 빈 목록이나 0으로 돌려준 경우
        given(rebuilder.rebuild(List.of(2L), 100L, 10L)).willReturn(0, 0, 7);
        HotArticleStartupRebuildRunner runner = new HotArticleStartupRebuildRunner(
                rebuilder, List.of(2L), 100, 10, 5, 0
        );

        boolean rebuilt = runner.rebuildWithRetry();

        assertThat(rebuilt).isTrue();
        verify(rebuilder, times(3)).rebuild(List.of(2L), 100L, 10L);
    }

    @Test
    void givesUpAfterMaxAttemptsWithoutThrowing() {
        HotArticleRebuilder rebuilder = mock(HotArticleRebuilder.class);
        given(rebuilder.rebuild(List.of(2L), 100L, 10L))
                .willThrow(new IllegalStateException("article-read down"));
        HotArticleStartupRebuildRunner runner = new HotArticleStartupRebuildRunner(
                rebuilder, List.of(2L), 100, 10, 3, 0
        );

        boolean rebuilt = runner.rebuildWithRetry();

        assertThat(rebuilt).isFalse();
        verify(rebuilder, times(3)).rebuild(List.of(2L), 100L, 10L);
    }
}
