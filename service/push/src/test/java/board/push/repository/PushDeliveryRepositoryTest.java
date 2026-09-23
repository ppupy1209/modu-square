package board.push.repository;

import board.push.domain.DeliveryClaim;
import board.push.domain.PushDevice;
import board.push.support.PushMySqlContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PushDeliveryRepository.class)
class PushDeliveryRepositoryTest {
    private static final Duration LEASE = Duration.ofSeconds(10);
    private static final long BROADCAST_ID = 1L;
    private static final long DEVICE_ID = 7L;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = PushMySqlContainer.create();

    @Autowired
    private PushDeliveryRepository deliveryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        deliveryRepository.insertPending(BROADCAST_ID, List.of(new PushDevice(DEVICE_ID, "device-7")));
    }

    @Test
    void 대기_중인_발송은_한_번만_선점된다() {
        assertThat(deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE)).isEqualTo(DeliveryClaim.CLAIMED);
        assertThat(deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE)).isEqualTo(DeliveryClaim.IN_PROGRESS);
    }

    @Test
    void 이미_보낸_발송은_다시_선점되지_않는다() {
        deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE);
        deliveryRepository.markSent(BROADCAST_ID, DEVICE_ID);

        assertThat(deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE)).isEqualTo(DeliveryClaim.ALREADY_DONE);
    }

    @Test
    void 일시_실패로_선점을_풀면_다시_보낼_수_있다() {
        deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE);
        deliveryRepository.release(BROADCAST_ID, DEVICE_ID);

        assertThat(deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE)).isEqualTo(DeliveryClaim.CLAIMED);
        assertThat(attemptCount()).isEqualTo(2);
    }

    @Test
    void 발송_중에_멈춰_lease가_지난_발송은_다시_선점된다() {
        deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE);
        jdbcTemplate.update(
                "UPDATE push_delivery SET updated_at = NOW(6) - INTERVAL 11 SECOND WHERE broadcast_id = ? AND device_id = ?",
                BROADCAST_ID, DEVICE_ID
        );

        assertThat(deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE)).isEqualTo(DeliveryClaim.CLAIMED);
    }

    @Test
    void 분배를_다시_실행해도_발송_단위는_그대로다() {
        deliveryRepository.claim(BROADCAST_ID, DEVICE_ID, LEASE);
        deliveryRepository.markSent(BROADCAST_ID, DEVICE_ID);

        deliveryRepository.insertPending(BROADCAST_ID, List.of(new PushDevice(DEVICE_ID, "device-7")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM push_delivery WHERE broadcast_id = ? AND device_id = ?",
                String.class, BROADCAST_ID, DEVICE_ID
        )).isEqualTo("SENT");
    }

    private int attemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM push_delivery WHERE broadcast_id = ? AND device_id = ?",
                Integer.class, BROADCAST_ID, DEVICE_ID
        );
    }
}
