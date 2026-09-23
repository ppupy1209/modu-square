package board.push.service;

import board.push.repository.PushDeliveryRepository;
import board.push.service.PushBroadcastService.AcceptResult;
import board.push.support.PushMySqlContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PushBroadcastService.class, PushDeliveryRepository.class})
// 접수는 운영처럼 요청마다 별도 트랜잭션으로 커밋돼야 unique key 충돌을 재현할 수 있다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushBroadcastServiceTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = PushMySqlContainer.create();

    @Autowired
    private PushBroadcastService broadcastService;

    @Test
    void 같은_키로_다시_요청하면_처음_접수한_발송을_돌려준다() {
        AcceptResult first = broadcastService.accept("key-duplicate", "점검 안내", "본문");
        AcceptResult second = broadcastService.accept("key-duplicate", "점검 안내", "본문");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.broadcastId()).isEqualTo(first.broadcastId());
    }

    @Test
    void 같은_키로_다른_내용을_요청하면_거절한다() {
        broadcastService.accept("key-conflict", "점검 안내", "본문");

        assertThatThrownBy(() -> broadcastService.accept("key-conflict", "다른 공지", "본문"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
