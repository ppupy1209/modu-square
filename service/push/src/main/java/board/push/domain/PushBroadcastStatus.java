package board.push.domain;

public enum PushBroadcastStatus {
    /** 접수만 된 상태. 발송 대상은 아직 만들지 않았다. */
    ACCEPTED,
    /** 발송 대상을 만들어 Kafka로 넘기는 중. */
    DISPATCHING,
    /** 발송 대상을 모두 Kafka로 넘겼다. 실제 발송 진행은 push_delivery로 본다. */
    DISPATCHED
}
