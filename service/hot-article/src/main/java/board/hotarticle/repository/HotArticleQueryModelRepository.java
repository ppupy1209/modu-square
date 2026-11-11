package board.hotarticle.repository;

import board.common.dataserializer.DataSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class HotArticleQueryModelRepository {
    private static final String KEY_FORMAT = "hot-article::article::%s::query-model";

    private final StringRedisTemplate redisTemplate;

    public void createOrUpdate(HotArticleQueryModel queryModel, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateKey(queryModel.getArticleId()),
                DataSerializer.serialize(queryModel),
                ttl
        );
    }

    /** 재구성 경로 전용. 이벤트가 먼저 쓴 값이 있으면 덮어쓰지 않는다. */
    public void createIfAbsent(HotArticleQueryModel queryModel, Duration ttl) {
        redisTemplate.opsForValue().setIfAbsent(
                generateKey(queryModel.getArticleId()),
                DataSerializer.serialize(queryModel),
                ttl
        );
    }

    public Map<Long, HotArticleQueryModel> readAll(List<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Map.of();
        }

        List<String> values = redisTemplate.opsForValue().multiGet(
                articleIds.stream().map(this::generateKey).toList()
        );
        if (values == null) {
            return Map.of();
        }

        Map<Long, HotArticleQueryModel> result = new LinkedHashMap<>();
        for (int index = 0; index < articleIds.size(); index++) {
            String value = index < values.size() ? values.get(index) : null;
            if (value == null) {
                continue;
            }
            HotArticleQueryModel queryModel = DataSerializer.deserialize(value, HotArticleQueryModel.class);
            if (queryModel != null) {
                result.put(queryModel.getArticleId(), queryModel);
            }
        }
        return result;
    }

    public void delete(Long articleId) {
        redisTemplate.delete(generateKey(articleId));
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }
}
