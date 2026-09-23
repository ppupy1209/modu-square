package board.push.repository;

import board.push.domain.PushBroadcast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushBroadcastRepository extends JpaRepository<PushBroadcast, Long> {
}
