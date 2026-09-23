package board.push.kafka;

import board.common.dataserializer.DataSerializer;
import board.push.domain.PushBroadcast;
import board.push.domain.PushDevice;
import board.push.domain.PushSendMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PushSendPublisher {
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    /** 페이지 하나를 보내고 broker 응답을 모두 받은 뒤 돌아온다. 하나라도 실패하면 예외로 분배를 멈춘다. */
    public void publish(PushBroadcast broadcast, List<PushDevice> devices) {
        CompletableFuture<?>[] results = devices.stream()
                .map(device -> kafkaTemplate.send(
                        PushTopics.SEND,
                        String.valueOf(device.deviceId()),
                        DataSerializer.serialize(new PushSendMessage(
                                broadcast.getBroadcastId(),
                                device.deviceId(),
                                device.token(),
                                broadcast.getTitle(),
                                broadcast.getBody()
                        ))
                ))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(results).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("push-send publish interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("push-send publish failed. broadcastId=" + broadcast.getBroadcastId(), e);
        }
    }
}
