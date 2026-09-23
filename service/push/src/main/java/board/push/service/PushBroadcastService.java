package board.push.service;

import board.push.client.PushProviderClient;
import board.push.domain.PushBroadcast;
import board.push.domain.PushDevice;
import board.push.metrics.PushMetrics;
import board.push.repository.PushBroadcastRepository;
import board.push.repository.PushDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushBroadcastService {
    private final PushBroadcastRepository broadcastRepository;
    private final PushDeviceRepository deviceRepository;
    private final PushProviderClient providerClient;
    private final PushMetrics metrics;

    /** 요청 스레드에서 수신 기기 전체에 순서대로 발송하고, 끝나면 결과를 돌려준다. */
    public BroadcastResult broadcast(String title, String body) {
        PushBroadcast broadcast = broadcastRepository.save(PushBroadcast.sending(title, body, LocalDateTime.now()));

        List<PushDevice> devices = deviceRepository.findAllEnabled();
        int sent = 0;
        int failed = 0;
        for (PushDevice device : devices) {
            try {
                providerClient.send(device.token(), title, body);
                sent++;
                metrics.recordDelivery("sent");
            } catch (RuntimeException e) {
                failed++;
                metrics.recordDelivery("failed");
                log.warn("[PushBroadcastService.broadcast] broadcastId={}, deviceId={}",
                        broadcast.getBroadcastId(), device.deviceId(), e);
            }
        }

        broadcast.complete(devices.size(), LocalDateTime.now());
        broadcastRepository.save(broadcast);
        return new BroadcastResult(broadcast.getBroadcastId(), devices.size(), sent, failed);
    }

    public record BroadcastResult(Long broadcastId, int targetCount, int sentCount, int failedCount) {
    }
}
