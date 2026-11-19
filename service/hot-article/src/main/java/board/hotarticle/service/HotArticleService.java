package board.hotarticle.service;

import board.common.event.Event;
import board.common.event.EventPayload;
import board.common.event.payload.EventType;
import board.hotarticle.client.ArticleClient;
import board.hotarticle.kafka.HotArticleEventPosition;
import board.hotarticle.kafka.InvalidHotArticleEventException;
import board.hotarticle.repository.ArticleCommentCountRepository;
import board.hotarticle.repository.ArticleLikeCountRepository;
import board.hotarticle.repository.ArticleViewCountRepository;
import board.hotarticle.repository.HotArticleListRepository;
import board.hotarticle.repository.HotArticleQueryModel;
import board.hotarticle.repository.HotArticleQueryModelRepository;
import board.hotarticle.service.eventhandler.EventHandler;
import board.hotarticle.service.response.HotArticleResponse;
import board.hotarticle.utils.TimeCalculatorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleService {
    private final ArticleClient articleClient;
    private final List<EventHandler> eventHandlers;
    private final HotArticleScoreUpdater hotArticleScoreUpdater;
    private final HotArticleListRepository hotArticleListRepository;
    private final HotArticleQueryModelRepository hotArticleQueryModelRepository;
    private final HotArticleReadModelMetrics hotArticleReadModelMetrics;
    private final HotArticleEventVersionGuard eventVersionGuard;
    private final ArticleLikeCountRepository articleLikeCountRepository;
    private final ArticleCommentCountRepository articleCommentCountRepository;
    private final ArticleViewCountRepository articleViewCountRepository;

    public boolean handleIfLatest(Event<EventPayload> event, HotArticleEventPosition position) {
        EventHandler<EventPayload> eventHandler = findEventHandler(event);
        if (eventHandler == null) {
            return false;
        }

        Long articleId = eventHandler.findArticleId(event);
        if (articleId == null) {
            throw new InvalidHotArticleEventException("Hot-article event has no articleId");
        }
        return eventVersionGuard.runIfLatest(
                position,
                articleId,
                () -> handleEvent(event, eventHandler)
        );
    }

    private void handleEvent(Event<EventPayload> event, EventHandler<EventPayload> eventHandler) {
        if (isArticleProjectionEvent(event)) {
            eventHandler.handle(event);
        } else {
            hotArticleScoreUpdater.update(event, eventHandler);
        }
    }

    private boolean isArticleProjectionEvent(Event<EventPayload> event) {
        return EventType.ARTICLE_CREATED == event.getType()
                || EventType.ARTICLE_UPDATED == event.getType()
                || EventType.ARTICLE_DELETED == event.getType();
    }

    private EventHandler<EventPayload> findEventHandler(Event<EventPayload> event) {
        return eventHandlers.stream()
                .filter(eventHandler -> eventHandler.supports(event))
                .findAny()
                .orElse(null);
    }

    public List<HotArticleResponse> readAll() {
        return readAll(TimeCalculatorUtils.calculateHotArticleDate());
    }

    List<HotArticleResponse> readAll(String dateStr) {
        List<Long> articleIds = hotArticleListRepository.readAll(dateStr);
        Map<Long, HotArticleQueryModel> queryModels = new LinkedHashMap<>(
                hotArticleQueryModelRepository.readAll(articleIds)
        );

        List<Long> missingArticleIds = articleIds.stream()
                .filter(articleId -> !queryModels.containsKey(articleId))
                .toList();
        hotArticleReadModelMetrics.hit(articleIds.size() - missingArticleIds.size());
        hotArticleReadModelMetrics.miss(missingArticleIds.size());

        Duration queryModelTtl = TimeCalculatorUtils.calculateDurationToExpiration(dateStr);
        for (Long articleId : missingArticleIds) {
            hotArticleReadModelMetrics.originCall(1);
            ArticleClient.ArticleResponse article = articleClient.read(articleId);
            if (article == null) {
                continue;
            }
            HotArticleQueryModel queryModel = HotArticleQueryModel.create(article);
            hotArticleQueryModelRepository.createOrUpdate(queryModel, queryModelTtl);
            queryModels.put(articleId, queryModel);
        }

        Map<Long, Long> likeCounts = articleLikeCountRepository.readAll(articleIds);
        Map<Long, Long> commentCounts = articleCommentCountRepository.readAll(articleIds);
        Map<Long, Long> viewCounts = articleViewCountRepository.readAll(articleIds);

        return articleIds.stream()
                .map(queryModels::get)
                .filter(Objects::nonNull)
                .map(queryModel -> HotArticleResponse.from(
                        queryModel,
                        likeCounts.getOrDefault(queryModel.getArticleId(), 0L),
                        commentCounts.getOrDefault(queryModel.getArticleId(), 0L),
                        viewCounts.getOrDefault(queryModel.getArticleId(), 0L)
                ))
                .toList();
    }

}
