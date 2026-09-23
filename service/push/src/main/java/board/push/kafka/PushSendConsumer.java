package board.push.kafka;

import board.common.dataserializer.DataSerializer;
import board.push.client.PushProviderClient;
import board.push.config.PushSendProperties;
import board.push.domain.DeliveryClaim;
import board.push.domain.PushSendMessage;
import board.push.metrics.PushMetrics;
import board.push.repository.PushDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushSendConsumer {
    private final PushDeliveryRepository deliveryRepository;
    private final PushProviderClient providerClient;
    private final PushSendProperties properties;
    private final PushMetrics metrics;

    @KafkaListener(topics = PushTopics.SEND, concurrency = "${push.send.concurrency}")
    public void send(String payload) {
        PushSendMessage message = DataSerializer.deserialize(payload, PushSendMessage.class);

        DeliveryClaim claim = deliveryRepository.claim(message.broadcastId(), message.deviceId(), properties.lease());
        switch (claim) {
            case ALREADY_DONE -> {
                metrics.recordDelivery("duplicate_skipped");
                return;
            }
            case NOT_FOUND -> {
                metrics.recordDelivery("not_found");
                return;
            }
            case IN_PROGRESS -> {
                metrics.recordDelivery("in_progress");
                throw new DeliveryInProgressException(message.broadcastId(), message.deviceId());
            }
            case CLAIMED -> {
            }
        }

        try {
            providerClient.send(message.token(), message.title(), message.body());
        } catch (RuntimeException e) {
            // 메시지를 잃지 않도록 성공할 때까지 같은 자리에서 다시 시도한다.
            deliveryRepository.release(message.broadcastId(), message.deviceId());
            metrics.recordDelivery("failed");
            throw e;
        }
        deliveryRepository.markSent(message.broadcastId(), message.deviceId());
        metrics.recordDelivery("sent");
    }
}
