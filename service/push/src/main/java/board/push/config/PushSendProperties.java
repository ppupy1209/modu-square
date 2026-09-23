package board.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("push.send")
public record PushSendProperties(int concurrency, Duration lease) {
}
