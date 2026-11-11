package board.hotarticle.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ArticleLikeCountRepository {
    private final StringRedisTemplate redisTemplate;

    private static final String KEF_FORMAT = "hot-article::article::%s::like-count";

    public void createOrUpdate(Long articleId, Long likeCount, Duration ttl) {
        redisTemplate.opsForValue().set(generateKey(articleId), String.valueOf(likeCount), ttl);
    }

    /** 재구성 경로 전용. 이벤트가 먼저 쓴 값이 있으면 덮어쓰지 않는다. */
    public void createIfAbsent(Long articleId, Long likeCount, Duration ttl) {
        redisTemplate.opsForValue().setIfAbsent(generateKey(articleId), String.valueOf(likeCount), ttl);
    }

    public Long read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return result == null ? 0L : Long.valueOf(result);
    }

    /** 인기글 목록 응답용. 키마다 왕복하지 않도록 한 번의 MGET으로 읽고, 없는 값은 0으로 본다. */
    public Map<Long, Long> readAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }
        List<String> values = redisTemplate.opsForValue().multiGet(
                articleIds.stream().map(this::generateKey).toList()
        );
        Map<Long, Long> result = new HashMap<>();
        for (int index = 0; index < articleIds.size(); index++) {
            String value = values == null || index >= values.size() ? null : values.get(index);
            result.put(articleIds.get(index), value == null ? 0L : Long.valueOf(value));
        }
        return result;
    }

    private String generateKey(Long articleId) {
        return KEF_FORMAT.formatted(articleId);
    }
}
