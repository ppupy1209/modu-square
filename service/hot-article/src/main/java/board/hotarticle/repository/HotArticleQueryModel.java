package board.hotarticle.repository;

import board.common.event.payload.ArticleCreatedEventPayload;
import board.common.event.payload.ArticleUpdatedEventPayload;
import board.hotarticle.client.ArticleClient;
import board.hotarticle.client.ArticleReadClient;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class HotArticleQueryModel {
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;

    public static HotArticleQueryModel create(ArticleCreatedEventPayload payload) {
        return create(payload.getArticleId(), payload.getTitle(), payload.getCreatedAt());
    }

    public static HotArticleQueryModel create(ArticleUpdatedEventPayload payload) {
        return create(payload.getArticleId(), payload.getTitle(), payload.getCreatedAt());
    }

    public static HotArticleQueryModel create(ArticleClient.ArticleResponse response) {
        return create(response.getArticleId(), response.getTitle(), response.getCreatedAt());
    }

    public static HotArticleQueryModel create(ArticleReadClient.ArticleReadResponse response) {
        return create(response.getArticleId(), response.getTitle(), response.getCreatedAt());
    }

    private static HotArticleQueryModel create(Long articleId, String title, LocalDateTime createdAt) {
        HotArticleQueryModel queryModel = new HotArticleQueryModel();
        queryModel.articleId = articleId;
        queryModel.title = title;
        queryModel.createdAt = createdAt;
        return queryModel;
    }
}
