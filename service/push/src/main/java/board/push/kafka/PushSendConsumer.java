package board.push.kafka;

import board.common.dataserializer.DataSerializer;
import board.push.client.PermanentPushFailureException;
import board.push.client.PushProviderClient;
import board.push.config.PushSendProperties;
import board.push.domain.DeliveryClaim;
import board.push.domain.PushSendMessage;
import board.push.metrics.PushMetrics;
import board.push.repository.PushDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 발송 단위 메시지 하나를 외부 업체로 보낸다.
 *
 * <p>실패한 메시지를 같은 partition에서 다시 시도하면 그 뒤의 정상 메시지가 모두 기다린다.
 * 그래서 일시 실패는 재시도 토픽으로 옮겨 시간을 두고 다시 보내고(push-send-retry-0~2),
 * 다시 보내도 결과가 같은 실패와 재시도를 다 쓴 실패는 DLT(push-send-dlt)에 격리한다.
 * 원래 토픽은 실패한 메시지를 넘긴 뒤 바로 다음 메시지를 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushSendConsumer {
    private final PushDeliveryRepository deliveryRepository;
    private final PushProviderClient providerClient;
    private final PushSendProperties properties;
    private final PushMetrics metrics;

    @RetryableTopic(
            attempts = "${push.send.retry.attempts}",
            backoff = @Backoff(
                    delayExpression = "${push.send.retry.delay-ms}",
                    multiplierExpression = "${push.send.retry.multiplier}"
            ),
            // 만료된 토큰처럼 업체가 4xx로 거절한 발송은 다시 보내도 결과가 같으므로 재시도 없이 DLT로 보낸다.
            exclude = PermanentPushFailureException.class,
            traversingCauses = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt",
            numPartitions = "6",
            replicationFactor = "1"
    )
    @KafkaListener(topics = PushTopics.SEND, concurrency = "${push.send.concurrency}")
    public void send(String payload) {
        PushSendMessage message = DataSerializer.deserialize(payload, PushSendMessage.class);

        DeliveryClaim claim = deliveryRepository.claim(message.broadcastId(), message.deviceId(), properties.lease());
        switch (claim) {
            case ALREADY_DONE -> {
                // 재전달이나 분배 재실행으로 같은 메시지가 다시 들어왔다. 이미 끝난 발송이라 보내지 않는다.
                metrics.recordDelivery("duplicate_skipped");
                return;
            }
            case NOT_FOUND -> {
                metrics.recordDelivery("not_found");
                return;
            }
            case IN_PROGRESS -> {
                // 선점한 처리가 업체 호출 도중 죽었을 수 있다. 버리면 아무도 보내지 않으므로 lease가 끝난 뒤 다시 확인한다.
                metrics.recordDelivery("in_progress");
                throw new DeliveryInProgressException(message.broadcastId(), message.deviceId());
            }
            case CLAIMED -> {
            }
        }

        try {
            providerClient.send(message.token(), message.title(), message.body());
        } catch (PermanentPushFailureException e) {
            deliveryRepository.markFailed(message.broadcastId(), message.deviceId(), "HTTP_" + e.getStatus());
            metrics.recordDelivery("permanent_failed");
            throw e;
        } catch (RuntimeException e) {
            deliveryRepository.release(message.broadcastId(), message.deviceId());
            metrics.recordDelivery("retryable_failed");
            throw e;
        }
        deliveryRepository.markSent(message.broadcastId(), message.deviceId());
        metrics.recordDelivery("sent");
    }

    /** DLT에 들어온 메시지는 원본을 그대로 보존하고, 발송 단위는 실패로 끝낸다. */
    @DltHandler
    public void isolate(
            String payload,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, required = false) String causeClass
    ) {
        PushSendMessage message = DataSerializer.deserialize(payload, PushSendMessage.class);
        deliveryRepository.markFailed(message.broadcastId(), message.deviceId(), "RETRY_EXHAUSTED");
        metrics.recordDelivery("dlt");
        log.warn("[PushSendConsumer.isolate] broadcastId={}, deviceId={}, cause={}",
                message.broadcastId(), message.deviceId(), causeClass);
    }
}
