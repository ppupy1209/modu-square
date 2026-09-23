package board.push.client;

import lombok.Getter;

/** 다시 보내도 성공하지 않는 발송 실패. 업체가 4xx로 거절한 경우다. */
@Getter
public class PermanentPushFailureException extends RuntimeException {
    private final int status;

    public PermanentPushFailureException(int status, Throwable cause) {
        super("push provider rejected message. status=" + status, cause);
        this.status = status;
    }
}
