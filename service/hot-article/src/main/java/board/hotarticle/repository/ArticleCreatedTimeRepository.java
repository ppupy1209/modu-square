package board.hotarticle.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class ArticleCreatedTimeRepository {
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_FORMAT = "hot-article::article::%s::created-time";

    public void createOrUpdate(Long articleId, LocalDateTime createdAt, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateKey(articleId),
                String.valueOf(createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()),
                ttl
        );
    }

    /** 재구성 경로 전용. 이벤트가 먼저 쓴 값이 있으면 덮어쓰지 않는다. */
    public void createIfAbsent(Long articleId, LocalDateTime createdAt, Duration ttl) {
        redisTemplate.opsForValue().setIfAbsent(
                generateKey(articleId),
                String.valueOf(createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()),
                ttl
        );
    }

    public void delete(Long articleId) {
        redisTemplate.delete(generateKey(articleId));
    }

    public LocalDateTime read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        if (result == null) return null;

        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.valueOf(result)), ZoneOffset.UTC
        );
    }


    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
