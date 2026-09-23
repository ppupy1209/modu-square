package board.push.client;

import board.push.config.PushProviderProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 외부 푸시 발송 업체 API 클라이언트.
 *
 * <p>업체 응답을 두 가지 실패로 나눈다.
 * 4xx는 같은 요청을 다시 보내도 결과가 바뀌지 않는 실패(만료된 토큰 등)이고,
 * 5xx와 타임아웃은 잠시 뒤 다시 보내면 성공할 수 있는 실패다.
 */
@Component
public class PushProviderClient {
    private final RestClient restClient;

    public PushProviderClient(PushProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(requestFactory)
                .build();
    }

    public void send(String token, String title, String body) {
        try {
            restClient.post()
                    .uri("/v1/messages")
                    .body(Map.of("token", token, "title", title, "body", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new PermanentPushFailureException(e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new RetryablePushFailureException(e);
        }
    }
}
