package board.push.repository;

import board.push.domain.PushDevice;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PushDeviceRepository {
    private final JdbcTemplate jdbcTemplate;

    /** device_id 기준 keyset 페이지. 수신 기기가 늘어도 페이지마다 같은 비용으로 읽는다. */
    public List<PushDevice> findEnabledAfter(long lastDeviceId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT device_id, token
                FROM push_device
                WHERE enabled = 1 AND device_id > ?
                ORDER BY device_id
                LIMIT ?
                """,
                (rs, rowNum) -> new PushDevice(rs.getLong("device_id"), rs.getString("token")),
                lastDeviceId,
                limit
        );
    }
}
