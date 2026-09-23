package board.push.kafka;

import board.common.dataserializer.DataSerializer;
import board.push.client.PermanentPushFailureException;
import board.push.client.PushProviderClient;
import board.push.client.RetryablePushFailureException;
import board.push.domain.PushDevice;
import board.push.domain.PushSendMessage;
import board.push.repository.PushDeliveryRepository;
import board.push.support.PushMySqlContainer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@EmbeddedKafka(kraft = true)
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "push.send.retry.delay-ms=100",
        "push.send.retry.multiplier=2",
        "push.send.lease=1s"
})
class PushSendConsumerIntegrationTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = PushMySqlContainer.create();

    @MockitoBean
    private PushProviderClient providerClient;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private PushDeliveryRepository deliveryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Test
    void 같은_발송_메시지가_두_번_들어와도_업체에는_한_번만_보낸다() {
        PushSendMessage message = prepare(101L, "device-101");
        doNothing().when(providerClient).send(eq("device-101"), any(), any());

        publish(message);
        publish(message);

        awaitStatus(message, "SENT");
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(providerClient, times(1)).send(eq("device-101"), any(), any()));
    }

    @Test
    void 업체가_거절한_발송은_재시도하지_않고_DLT로_격리한다() {
        PushSendMessage message = prepare(102L, "expired-102");
        doThrow(new PermanentPushFailureException(410, null))
                .when(providerClient).send(eq("expired-102"), any(), any());

        publish(message);

        awaitStatus(message, "FAILED");
        assertThat(failureReason(message)).isEqualTo("HTTP_410");
        assertThat(dltRecordFor(message)).isNotNull();
        verify(providerClient, times(1)).send(eq("expired-102"), any(), any());
    }

    @Test
    void 일시_실패가_계속되면_네_번_시도한_뒤_DLT로_격리한다() {
        PushSendMessage message = prepare(103L, "stuck-103");
        doThrow(new RetryablePushFailureException(null))
                .when(providerClient).send(eq("stuck-103"), any(), any());

        publish(message);

        await().atMost(Duration.ofSeconds(20)).until(() -> "RETRY_EXHAUSTED".equals(failureReason(message)));
        verify(providerClient, times(4)).send(eq("stuck-103"), any(), any());
        assertThat(dltRecordFor(message)).isNotNull();
    }

    @Test
    void 일시_실패는_재시도_토픽에서_다시_보내_성공한다() {
        PushSendMessage message = prepare(104L, "device-104");
        doThrow(new RetryablePushFailureException(null))
                .doNothing()
                .when(providerClient).send(eq("device-104"), any(), any());

        publish(message);

        awaitStatus(message, "SENT");
        verify(providerClient, times(2)).send(eq("device-104"), any(), any());
    }

    private PushSendMessage prepare(Long deviceId, String token) {
        long broadcastId = 1L;
        deliveryRepository.insertPending(broadcastId, List.of(new PushDevice(deviceId, token)));
        return new PushSendMessage(broadcastId, deviceId, token, "점검 안내", "본문");
    }

    private void publish(PushSendMessage message) {
        kafkaTemplate.send(PushTopics.SEND, String.valueOf(message.deviceId()), DataSerializer.serialize(message)).join();
    }

    private void awaitStatus(PushSendMessage message, String status) {
        await().atMost(Duration.ofSeconds(20)).until(() -> status.equals(deliveryStatus(message)));
    }

    private String deliveryStatus(PushSendMessage message) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM push_delivery WHERE broadcast_id = ? AND device_id = ?",
                String.class, message.broadcastId(), message.deviceId()
        );
    }

    private String failureReason(PushSendMessage message) {
        return jdbcTemplate.queryForObject(
                "SELECT failure_reason FROM push_delivery WHERE broadcast_id = ? AND device_id = ?",
                String.class, message.broadcastId(), message.deviceId()
        );
    }

    private ConsumerRecord<String, String> dltRecordFor(PushSendMessage message) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-verifier-" + message.deviceId(), "false", embeddedKafka);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(PushTopics.SEND + "-dlt"));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (String.valueOf(message.deviceId()).equals(record.key())) {
                        return record;
                    }
                }
            }
            return null;
        }
    }
}
