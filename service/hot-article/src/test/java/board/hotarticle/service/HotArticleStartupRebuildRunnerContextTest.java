package board.hotarticle.service;

import board.hotarticle.client.ArticleReadClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 애플리케이션 설정으로 재구성 러너가 만들어지는지 확인한다.
 * 게시글 조회 서비스 주소는 열려 있지 않은 포트라 백그라운드 시도는 1번 실패하고 끝난다.
 */
@SpringBootTest(properties = {
        "modu.hot-article.rebuild.enabled=true",
        "modu.hot-article.rebuild.board-ids=2,3",
        "modu.hot-article.rebuild.max-attempts=1",
        "modu.hot-article.rebuild.retry-interval-ms=0",
        "endpoints.board-article-read-service.url=http://127.0.0.1:1"
})
class HotArticleStartupRebuildRunnerContextTest {
    @Autowired
    HotArticleStartupRebuildRunner runner;
    @Autowired
    ArticleReadClient articleReadClient;

    @Test
    void bindsRebuildSettingsFromApplicationProperties() {
        assertThat(ReflectionTestUtils.getField(runner, "boardIds")).isEqualTo(List.of(2L, 3L));
        assertThat(ReflectionTestUtils.getField(runner, "maxAttempts")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(articleReadClient, "articleReadServiceUrl"))
                .isEqualTo("http://127.0.0.1:1");
    }
}
