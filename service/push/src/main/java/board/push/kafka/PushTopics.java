package board.push.kafka;

public final class PushTopics {
    /** 발송 단위 메시지. key는 device_id라 같은 기기의 메시지는 같은 partition에서 순서대로 처리된다. */
    public static final String SEND = "push-send";

    private PushTopics() {
    }
}
