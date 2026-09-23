package board.push.service;

import board.push.config.PushDispatchProperties;
import board.push.domain.PushBroadcast;
import board.push.domain.PushBroadcastStatus;
import board.push.domain.PushDevice;
import board.push.kafka.PushSendPublisher;
import board.push.repository.PushBroadcastRepository;
import board.push.repository.PushDeliveryRepository;
import board.push.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 접수된 발송을 기기 단위로 나눠 Kafka로 넘긴다.
 *
 * <p>접수 API는 DB에만 쓰고, Kafka 발행은 이 스케줄러가 DB 상태를 보고 한다.
 * 그래서 "DB에는 저장됐는데 Kafka 발행은 실패"하는 이중 쓰기 문제가 생기지 않는다.
 * 분배 도중 인스턴스가 죽으면 heartbeat가 끊긴 발송을 처음부터 다시 분배한다.
 * 이미 만든 발송 단위는 INSERT IGNORE로, 이미 보낸 메시지는 Consumer의 선점 UPDATE로 걸러진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushBroadcastDispatcher {
    private static final Duration STALE_AFTER = Duration.ofMinutes(1);

    private final PushBroadcastRepository broadcastRepository;
    private final PushDeviceRepository deviceRepository;
    private final PushDeliveryRepository deliveryRepository;
    private final PushSendPublisher publisher;
    private final PushDispatchProperties properties;

    @Scheduled(fixedDelayString = "${push.dispatch.interval}")
    public void dispatch() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minus(STALE_AFTER);
        broadcastRepository.findDispatchCandidate(staleBefore)
                .filter(broadcast -> broadcastRepository.claimDispatch(broadcast.getBroadcastId(), now, staleBefore) == 1)
                .ifPresent(this::dispatch);
    }

    private void dispatch(PushBroadcast broadcast) {
        Long broadcastId = broadcast.getBroadcastId();
        long lastDeviceId = 0;
        int targetCount = 0;
        while (true) {
            List<PushDevice> devices = deviceRepository.findEnabledAfter(lastDeviceId, properties.pageSize());
            if (devices.isEmpty()) {
                break;
            }
            deliveryRepository.insertPending(broadcastId, devices);
            publisher.publish(broadcast, devices);
            broadcastRepository.touch(broadcastId, LocalDateTime.now());

            targetCount += devices.size();
            lastDeviceId = devices.getLast().deviceId();
        }
        broadcastRepository.markDispatched(broadcastId, PushBroadcastStatus.DISPATCHED, targetCount, LocalDateTime.now());
        log.info("[PushBroadcastDispatcher.dispatch] broadcastId={}, targetCount={}", broadcastId, targetCount);
    }
}
