package board.push.repository;

import board.push.domain.PushBroadcast;
import board.push.domain.PushBroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PushBroadcastRepository extends JpaRepository<PushBroadcast, Long> {
    Optional<PushBroadcast> findByIdempotencyKey(String idempotencyKey);

    /**
     * 분배할 발송 하나를 고른다. 접수된 발송과, 분배하던 인스턴스가 멈춰 heartbeat가 끊긴 발송이 대상이다.
     */
    @Query("""
            select b from PushBroadcast b
            where b.status = board.push.domain.PushBroadcastStatus.ACCEPTED
               or (b.status = board.push.domain.PushBroadcastStatus.DISPATCHING and b.updatedAt < :staleBefore)
            order by b.broadcastId asc
            limit 1
            """)
    Optional<PushBroadcast> findDispatchCandidate(@Param("staleBefore") LocalDateTime staleBefore);

    /** 조건부 UPDATE로 분배 권한을 가져온다. 영향받은 행이 1이면 이 인스턴스가 분배한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PushBroadcast b
            set b.status = board.push.domain.PushBroadcastStatus.DISPATCHING, b.updatedAt = :now
            where b.broadcastId = :broadcastId
              and (b.status = board.push.domain.PushBroadcastStatus.ACCEPTED
                   or (b.status = board.push.domain.PushBroadcastStatus.DISPATCHING and b.updatedAt < :staleBefore))
            """)
    int claimDispatch(@Param("broadcastId") Long broadcastId,
                      @Param("now") LocalDateTime now,
                      @Param("staleBefore") LocalDateTime staleBefore);

    /** 분배 중임을 알리는 heartbeat. 페이지 하나를 넘길 때마다 갱신한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PushBroadcast b set b.updatedAt = :now where b.broadcastId = :broadcastId")
    int touch(@Param("broadcastId") Long broadcastId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PushBroadcast b
            set b.status = :status, b.targetCount = :targetCount, b.updatedAt = :now
            where b.broadcastId = :broadcastId
            """)
    int markDispatched(@Param("broadcastId") Long broadcastId,
                       @Param("status") PushBroadcastStatus status,
                       @Param("targetCount") int targetCount,
                       @Param("now") LocalDateTime now);
}
