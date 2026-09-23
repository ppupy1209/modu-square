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

    public List<PushDevice> findAllEnabled() {
        return jdbcTemplate.query(
                "SELECT device_id, token FROM push_device WHERE enabled = 1 ORDER BY device_id",
                (rs, rowNum) -> new PushDevice(rs.getLong("device_id"), rs.getString("token"))
        );
    }
}
