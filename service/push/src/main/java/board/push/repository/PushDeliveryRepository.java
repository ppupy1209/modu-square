package board.push.repository;

import board.push.domain.DeliveryClaim;
import board.push.domain.PushDevice;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 발송 단위(push_delivery)의 상태를 바꾼다.
 *
 * <p>모든 상태 변경은 현재 상태를 조건으로 건 단일 UPDATE다.
 * 같은 발송 메시지가 두 번 들어와도 affected rows로 누가 먼저 처리했는지 구분한다.
 */
@Repository
@RequiredArgsConstructor
public class PushDeliveryRepository {
    private final JdbcTemplate jdbcTemplate;

    /** 분배를 다시 실행해도 이미 만든 발송 단위는 건드리지 않는다. */
    public void insertPending(Long broadcastId, List<PushDevice> devices) {
        jdbcTemplate.batchUpdate(
                """
                INSERT IGNORE INTO push_delivery (broadcast_id, device_id, token, status, updated_at)
                VALUES (?, ?, ?, 'PENDING', NOW(6))
                """,
                devices,
                devices.size(),
                (ps, device) -> {
                    ps.setLong(1, broadcastId);
                    ps.setLong(2, device.deviceId());
                    ps.setString(3, device.token());
                }
        );
    }

    /**
     * 발송 권한을 가져온다.
     *
     * <p>PENDING이거나, SENDING인데 lease가 지난 건만 SENDING으로 바꾼다.
     * lease가 지난 SENDING은 처리하던 인스턴스가 업체 호출 도중 죽은 경우다.
     */
    public DeliveryClaim claim(Long broadcastId, Long deviceId, Duration lease) {
        int updated = jdbcTemplate.update(
                """
                UPDATE push_delivery
                SET status = 'SENDING', attempt_count = attempt_count + 1, updated_at = NOW(6)
                WHERE broadcast_id = ? AND device_id = ?
                  AND (status = 'PENDING'
                       OR (status = 'SENDING' AND updated_at < NOW(6) - INTERVAL ? SECOND))
                """,
                broadcastId, deviceId, lease.toSeconds()
        );
        if (updated == 1) {
            return DeliveryClaim.CLAIMED;
        }

        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM push_delivery WHERE broadcast_id = ? AND device_id = ?",
                String.class, broadcastId, deviceId
        );
        if (status.isEmpty()) {
            return DeliveryClaim.NOT_FOUND;
        }
        return "SENDING".equals(status.getFirst()) ? DeliveryClaim.IN_PROGRESS : DeliveryClaim.ALREADY_DONE;
    }

    public void markSent(Long broadcastId, Long deviceId) {
        jdbcTemplate.update(
                """
                UPDATE push_delivery
                SET status = 'SENT', sent_at = NOW(6), updated_at = NOW(6)
                WHERE broadcast_id = ? AND device_id = ? AND status = 'SENDING'
                """,
                broadcastId, deviceId
        );
    }

    /** 잠시 뒤 다시 보낼 수 있게 선점을 푼다. */
    public void release(Long broadcastId, Long deviceId) {
        jdbcTemplate.update(
                """
                UPDATE push_delivery
                SET status = 'PENDING', updated_at = NOW(6)
                WHERE broadcast_id = ? AND device_id = ? AND status = 'SENDING'
                """,
                broadcastId, deviceId
        );
    }

    public void markFailed(Long broadcastId, Long deviceId, String reason) {
        jdbcTemplate.update(
                """
                UPDATE push_delivery
                SET status = 'FAILED', failure_reason = ?, updated_at = NOW(6)
                WHERE broadcast_id = ? AND device_id = ? AND status IN ('PENDING', 'SENDING')
                """,
                reason, broadcastId, deviceId
        );
    }

    public Map<DeliveryStatus, Long> countByStatus(Long broadcastId) {
        Map<DeliveryStatus, Long> counts = new EnumMap<>(DeliveryStatus.class);
        for (DeliveryStatus status : DeliveryStatus.values()) {
            counts.put(status, 0L);
        }
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM push_delivery WHERE broadcast_id = ? GROUP BY status",
                rs -> {
                    counts.put(DeliveryStatus.valueOf(rs.getString("status")), rs.getLong("cnt"));
                },
                broadcastId
        );
        return counts;
    }

    public enum DeliveryStatus {
        PENDING, SENDING, SENT, FAILED
    }
}
