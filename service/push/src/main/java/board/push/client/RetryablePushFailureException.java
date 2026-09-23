package board.push.client;

/** 잠시 뒤 다시 보내면 성공할 수 있는 발송 실패. 업체 5xx와 타임아웃이다. */
public class RetryablePushFailureException extends RuntimeException {
    public RetryablePushFailureException(Throwable cause) {
        super("push provider temporarily failed", cause);
    }
}
