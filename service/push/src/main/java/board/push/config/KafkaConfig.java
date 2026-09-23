package board.push.config;

import board.push.kafka.PushTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    /**
     * Consumer 동시성(push.send.concurrency)만큼 partition을 둬야 병렬로 보낸다.
     * 재시도 토픽과 DLT는 @RetryableTopic이 같은 partition 수로 만든다.
     */
    @Bean
    public NewTopic pushSendTopic() {
        return TopicBuilder.name(PushTopics.SEND).partitions(6).replicas(1).build();
    }
}
