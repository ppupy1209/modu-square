package board.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("push.dispatch")
public record PushDispatchProperties(Duration interval, int pageSize) {
}
