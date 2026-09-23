package board.push.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "push_broadcast")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushBroadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long broadcastId;

    @Column(length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushBroadcastStatus status;

    @Column(nullable = false)
    private int targetCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static PushBroadcast sending(String title, String body, LocalDateTime now) {
        PushBroadcast broadcast = new PushBroadcast();
        broadcast.title = title;
        broadcast.body = body;
        broadcast.status = PushBroadcastStatus.SENDING;
        broadcast.createdAt = now;
        broadcast.updatedAt = now;
        return broadcast;
    }

    public void complete(int targetCount, LocalDateTime now) {
        this.targetCount = targetCount;
        this.status = PushBroadcastStatus.COMPLETED;
        this.updatedAt = now;
    }
}
