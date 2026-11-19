package board.hotarticle.client;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 인기글 재구성에 쓰는 게시글 조회 서비스 클라이언트.
 *
 * <p>{@link ArticleClient}와 달리 실패를 삼키지 않는다. 호출한 쪽이 실패를 보고 다시 시도해야 하기 때문이다.
 */
@Component
public class ArticleReadClient {
    private RestClient restClient;
    @Value("${endpoints.board-article-read-service.url}")
    private String articleReadServiceUrl;

    @PostConstruct
    void initRestClient() {
        restClient = RestClient.create(articleReadServiceUrl);
    }

    public List<ArticleReadResponse> readAll(Long boardId, long page, long pageSize) {
        ArticleReadPageResponse response = restClient.get()
                .uri("/v1/articles?boardId={boardId}&page={page}&pageSize={pageSize}", boardId, page, pageSize)
                .retrieve()
                .body(ArticleReadPageResponse.class);
        if (response == null || response.getArticles() == null) {
            return List.of();
        }
        return response.getArticles();
    }

    @Getter
    public static class ArticleReadPageResponse {
        private List<ArticleReadResponse> articles;
        private Long articleCount;
    }

    @Getter
    public static class ArticleReadResponse {
        private Long articleId;
        private String title;
        private LocalDateTime createdAt;
        private Long articleCommentCount;
        private Long articleLikeCount;
        private Long articleViewCount;
    }
}
