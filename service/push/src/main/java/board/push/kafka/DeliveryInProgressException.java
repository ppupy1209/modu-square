package board.push.kafka;

/**
 * 다른 처리가 같은 발송 단위를 선점하고 있다.
 * 그 처리가 업체 호출 도중 죽었다면 lease가 끝난 뒤 이 메시지가 가져가야 하므로, 버리지 않고 다시 시도한다.
 */
public class DeliveryInProgressException extends RuntimeException {
    public DeliveryInProgressException(Long broadcastId, Long deviceId) {
        super("delivery is being sent by another consumer. broadcastId=" + broadcastId + ", deviceId=" + deviceId);
    }
}
