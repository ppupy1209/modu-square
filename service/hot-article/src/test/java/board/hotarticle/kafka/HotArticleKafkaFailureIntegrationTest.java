package board.hotarticle.kafka;

import board.common.event.payload.EventType;
import board.hotarticle.config.KafkaConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HotArticleKafkaFailureIntegrationTest.TestApplication.class,
        properties = {
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.group-id=modu-square-hot-article-service",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.enable-auto-commit=false",
                "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "modu.kafka.failure.retry-interval-ms=10",
                "modu.kafka.failure.max-attempts=3",
                "modu.hot-article.rebuild.enabled=false"
        }
)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                HotArticleKafkaFailureIntegrationTest.RETRYABLE_INPUT_TOPIC,
                HotArticleKafkaFailureIntegrationTest.DLQ_TOPIC,
                HotArticleKafkaFailureIntegrationTest.PERMANENT_INPUT_TOPIC,
                HotArticleKafkaFailureIntegrationTest.PARKING_TOPIC
        }
)
@DirtiesContext
class HotArticleKafkaFailureIntegrationTest {
    static final String RETRYABLE_INPUT_TOPIC = EventType.Topic.BOARD_VIEW;
    static final String DLQ_TOPIC = RETRYABLE_INPUT_TOPIC + HotArticleKafkaTopics.DLQ_SUFFIX;
    static final String PERMANENT_INPUT_TOPIC = EventType.Topic.BOARD_LIKE;
    static final String PARKING_TOPIC = PERMANENT_INPUT_TOPIC + HotArticleKafkaTopics.PARKING_SUFFIX;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;
    @Autowired
    private MeterRegistry meterRegistry;

    private Consumer<String, String> dlqConsumer;
    private Consumer<String, String> parkingConsumer;

    @BeforeEach
    void setUp() {
        FailureListener.RETRYABLE_ATTEMPTS.set(0);
        FailureListener.PERMANENT_ATTEMPTS.set(0);
        dlqConsumer = createConsumer(DLQ_TOPIC);
        parkingConsumer = createConsumer(PARKING_TOPIC);
    }

    @AfterEach
    void tearDown() {
        dlqConsumer.close();
        parkingConsumer.close();
    }

    @Test
    void retriesTransientFailureThreeTimesThenPublishesToDlq() {
        kafkaTemplate.send(RETRYABLE_INPUT_TOPIC, "article-1", "transient-event").join();

        ConsumerRecord<String, String> dlqRecord = KafkaTestUtils.getSingleRecord(
                dlqConsumer,
                DLQ_TOPIC,
                Duration.ofSeconds(10)
        );

        assertThat(FailureListener.RETRYABLE_ATTEMPTS).hasValue(3);
        assertThat(dlqRecord.key()).isEqualTo("article-1");
        assertThat(dlqRecord.value()).isEqualTo("transient-event");
        assertThat(originalTopic(dlqRecord)).isEqualTo(RETRYABLE_INPUT_TOPIC);
        assertThat(meterRegistry.get("modu.kafka.delivery")
                .tag("topic", RETRYABLE_INPUT_TOPIC)
                .tag("result", "retry")
                .counter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("modu.kafka.delivery")
                .tag("topic", RETRYABLE_INPUT_TOPIC)
                .tag("result", "dlq")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void parksPermanentFailureImmediatelyWithoutRetry() {
        kafkaTemplate.send(PERMANENT_INPUT_TOPIC, "article-2", "invalid-event").join();

        ConsumerRecord<String, String> parkingRecord = KafkaTestUtils.getSingleRecord(
                parkingConsumer,
                PARKING_TOPIC,
                Duration.ofSeconds(10)
        );

        assertThat(FailureListener.PERMANENT_ATTEMPTS).hasValue(1);
        assertThat(parkingRecord.key()).isEqualTo("article-2");
        assertThat(parkingRecord.value()).isEqualTo("invalid-event");
        assertThat(originalTopic(parkingRecord)).isEqualTo(PERMANENT_INPUT_TOPIC);
        assertThat(meterRegistry.get("modu.kafka.delivery")
                .tag("topic", PERMANENT_INPUT_TOPIC)
                .tag("result", "parking")
                .counter().count()).isEqualTo(1);
    }

    private Consumer<String, String> createConsumer(String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                UUID.randomUUID().toString(),
                "false",
                embeddedKafka
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private String originalTopic(ConsumerRecord<String, String> record) {
        return new String(
                record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                StandardCharsets.UTF_8
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({KafkaConfig.class, FailureListener.class})
    static class TestApplication {
    }

    static class FailureListener {
        static final AtomicInteger RETRYABLE_ATTEMPTS = new AtomicInteger();
        static final AtomicInteger PERMANENT_ATTEMPTS = new AtomicInteger();

        @KafkaListener(
                id = "hotArticleRetryableFailureIntegrationListener",
                topics = RETRYABLE_INPUT_TOPIC,
                groupId = "hot-article-retryable-failure-it-group",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void retryable(String message, Acknowledgment acknowledgment) {
            RETRYABLE_ATTEMPTS.incrementAndGet();
            throw new RedisConnectionFailureException("forced transient failure");
        }

        @KafkaListener(
                id = "hotArticlePermanentFailureIntegrationListener",
                topics = PERMANENT_INPUT_TOPIC,
                groupId = "hot-article-permanent-failure-it-group",
                containerFactory = "kafkaListenerContainerFactory"
        )
        void permanent(String message, Acknowledgment acknowledgment) {
            PERMANENT_ATTEMPTS.incrementAndGet();
            throw new InvalidHotArticleEventException("forced permanent failure");
        }
    }
}
