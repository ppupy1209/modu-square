package board.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("push.provider")
public record PushProviderProperties(String url, Duration connectTimeout, Duration readTimeout) {
}
