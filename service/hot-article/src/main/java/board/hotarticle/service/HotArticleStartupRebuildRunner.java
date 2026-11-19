package board.hotarticle.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 서비스가 뜬 뒤 백그라운드에서 오늘의 인기글을 한 번 재구성한다.
 *
 * <p>게시글 조회 서비스가 아직 준비되지 않았을 수 있어 정해진 횟수만큼 간격을 두고 다시 시도한다.
 * 게시글 조회 서비스는 하위 서비스 장애를 빈 목록이나 0으로 돌려주므로, 한 건도 채우지 못한 시도도 실패로 보고 다시 시도한다.
 * 끝내 실패해도 서비스 기동과 이벤트 소비에는 영향을 주지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "modu.hot-article.rebuild", name = "enabled", havingValue = "true")
public class HotArticleStartupRebuildRunner {
    private final HotArticleRebuilder hotArticleRebuilder;
    private final List<Long> boardIds;
    private final long pageSize;
    private final long maxPages;
    private final int maxAttempts;
    private final Duration retryInterval;

    private volatile Thread worker;

    public HotArticleStartupRebuildRunner(
            HotArticleRebuilder hotArticleRebuilder,
            @Value("${modu.hot-article.rebuild.board-ids:2}") List<Long> boardIds,
            @Value("${modu.hot-article.rebuild.page-size:100}") long pageSize,
            @Value("${modu.hot-article.rebuild.max-pages:10}") long maxPages,
            @Value("${modu.hot-article.rebuild.max-attempts:12}") int maxAttempts,
            @Value("${modu.hot-article.rebuild.retry-interval-ms:10000}") long retryIntervalMs
    ) {
        this.hotArticleRebuilder = hotArticleRebuilder;
        this.boardIds = List.copyOf(boardIds);
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.maxAttempts = maxAttempts;
        this.retryInterval = Duration.ofMillis(retryIntervalMs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startRebuild() {
        Thread thread = new Thread(this::rebuildWithRetry, "hot-article-rebuild");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    /** @return 한 건 이상 다시 채웠으면 true */
    boolean rebuildWithRetry() {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                int rebuilt = hotArticleRebuilder.rebuild(boardIds, pageSize, maxPages);
                if (rebuilt > 0) {
                    log.info("[HotArticleStartupRebuildRunner] rebuilt {} hot articles (attempt {})", rebuilt, attempt);
                    return true;
                }
                log.info("[HotArticleStartupRebuildRunner] nothing to rebuild yet (attempt {}/{})", attempt, maxAttempts);
            } catch (Exception e) {
                log.warn("[HotArticleStartupRebuildRunner] rebuild failed (attempt {}/{}): {}",
                        attempt, maxAttempts, e.toString());
            }
            if (attempt < maxAttempts && !sleep()) {
                return false;
            }
        }
        log.info("[HotArticleStartupRebuildRunner] stopped after {} attempts; events will fill the ranking", maxAttempts);
        return false;
    }

    private boolean sleep() {
        try {
            Thread.sleep(retryInterval);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void stop() {
        Thread thread = worker;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
