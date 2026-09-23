package board.push.domain;

/** 발송 단위 하나를 보낼 권한을 가져온 결과. */
public enum DeliveryClaim {
    /** 이번 처리가 발송한다. */
    CLAIMED,
    /** 이미 발송했거나 실패로 끝났다. 같은 메시지가 다시 들어온 경우다. */
    ALREADY_DONE,
    /** 다른 처리가 발송 중이다. 그 처리가 죽었을 수도 있으므로 버리지 않고 나중에 다시 확인한다. */
    IN_PROGRESS,
    /** 발송 단위가 없다. */
    NOT_FOUND
}
