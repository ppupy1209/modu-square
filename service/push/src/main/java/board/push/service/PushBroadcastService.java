package board.push.service;

import board.push.domain.PushBroadcast;
import board.push.domain.PushBroadcastStatus;
import board.push.repository.PushBroadcastRepository;
import board.push.repository.PushDeliveryRepository;
import board.push.repository.PushDeliveryRepository.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PushBroadcastService {
    private final PushBroadcastRepository broadcastRepository;
    private final PushDeliveryRepository deliveryRepository;

    /**
     * 발송을 접수만 한다. 발송 대상을 만들고 보내는 일은 {@link PushBroadcastDispatcher}와 Consumer가 한다.
     *
     * <p>같은 Idempotency-Key로 다시 요청하면 새로 접수하지 않고 처음 접수한 발송을 돌려준다.
     * 운영자의 더블 클릭이나 응답을 받지 못한 클라이언트의 재전송이 발송을 두 번 만들지 않게 하기 위해서다.
     */
    public AcceptResult accept(String idempotencyKey, String title, String body) {
        var existing = broadcastRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return AcceptResult.existing(verifySameRequest(existing.get(), title, body));
        }
        try {
            PushBroadcast saved = broadcastRepository.saveAndFlush(
                    PushBroadcast.accept(idempotencyKey, title, body, LocalDateTime.now())
            );
            return AcceptResult.created(saved);
        } catch (DataIntegrityViolationException e) {
            // 같은 키의 요청이 동시에 들어와 다른 요청이 먼저 저장했다. unique key가 한 건만 남긴다.
            PushBroadcast winner = broadcastRepository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return AcceptResult.existing(verifySameRequest(winner, title, body));
        }
    }

    public BroadcastStatus readStatus(Long broadcastId) {
        PushBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<DeliveryStatus, Long> counts = deliveryRepository.countByStatus(broadcastId);
        long remaining = counts.get(DeliveryStatus.PENDING) + counts.get(DeliveryStatus.SENDING);
        boolean completed = broadcast.getStatus() == PushBroadcastStatus.DISPATCHED && remaining == 0;
        return new BroadcastStatus(
                broadcastId,
                completed ? "COMPLETED" : broadcast.getStatus().name(),
                broadcast.getTargetCount(),
                counts.get(DeliveryStatus.SENT),
                counts.get(DeliveryStatus.FAILED),
                remaining
        );
    }

    /** 키가 같은데 내용이 다르면 클라이언트가 키를 잘못 재사용한 것이다. 조용히 첫 요청을 돌려주지 않는다. */
    private PushBroadcast verifySameRequest(PushBroadcast broadcast, String title, String body) {
        if (!Objects.equals(broadcast.getTitle(), title) || !Objects.equals(broadcast.getBody(), body)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 내용을 요청했습니다.");
        }
        return broadcast;
    }

    public record AcceptResult(Long broadcastId, String status, boolean created) {
        static AcceptResult created(PushBroadcast broadcast) {
            return new AcceptResult(broadcast.getBroadcastId(), broadcast.getStatus().name(), true);
        }

        static AcceptResult existing(PushBroadcast broadcast) {
            return new AcceptResult(broadcast.getBroadcastId(), broadcast.getStatus().name(), false);
        }
    }

    public record BroadcastStatus(
            Long broadcastId,
            String status,
            int targetCount,
            long sentCount,
            long failedCount,
            long remainingCount
    ) {
    }
}
