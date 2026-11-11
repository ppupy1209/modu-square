package board.hotarticle.service.response;

import board.hotarticle.repository.HotArticleQueryModel;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString
public class HotArticleResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long articleId;
    private String title;
    private LocalDateTime createdAt;
    /** 점수 계산에 쓴 것과 같은 Redis 값이다. 화면이 순위와 다른 숫자를 보여주지 않게 한다. */
    private Long articleLikeCount;
    private Long articleCommentCount;
    private Long articleViewCount;

    public static HotArticleResponse from(
            HotArticleQueryModel queryModel,
            Long articleLikeCount,
            Long articleCommentCount,
            Long articleViewCount
    ) {
        HotArticleResponse hotArticleResponse = new HotArticleResponse();
        hotArticleResponse.articleId = queryModel.getArticleId();
        hotArticleResponse.title = queryModel.getTitle();
        hotArticleResponse.createdAt = queryModel.getCreatedAt();
        hotArticleResponse.articleLikeCount = articleLikeCount;
        hotArticleResponse.articleCommentCount = articleCommentCount;
        hotArticleResponse.articleViewCount = articleViewCount;
        return hotArticleResponse;
    }
}
