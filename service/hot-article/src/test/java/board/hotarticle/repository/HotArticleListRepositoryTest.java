package board.hotarticle.repository;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "modu.hot-article.rebuild.enabled=false")
class HotArticleListRepositoryTest {
    @Autowired
    HotArticleListRepository hotArticleListRepository;

    @Test
    void addTest() throws InterruptedException {
        LocalDateTime time = LocalDateTime.of(2024, 7, 23, 0, 0);
        long limit = 3;

        hotArticleListRepository.add(1L, time, 2L, limit, Duration.ofSeconds(3));
        hotArticleListRepository.add(2L, time, 3L, limit, Duration.ofSeconds(3));
        hotArticleListRepository.add(3L, time, 1L, limit, Duration.ofSeconds(3));
        hotArticleListRepository.add(4L, time, 5L, limit, Duration.ofSeconds(3));
        hotArticleListRepository.add(5L, time, 4L, limit, Duration.ofSeconds(3));

        List<Long> articleIds = hotArticleListRepository.readAll("20240723");

        assertThat(articleIds).hasSize(Long.valueOf(limit).intValue());
        assertThat(articleIds.get(0)).isEqualTo(4L);

        TimeUnit.SECONDS.sleep(5);

        assertThat(hotArticleListRepository.readAll("20240723")).isEmpty();
    }

}
