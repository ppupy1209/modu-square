package board.push.domain;

/**
 * Kafka로 넘기는 발송 단위 메시지. 공지 하나를 기기 하나에 보낸다.
 * Consumer가 DB를 다시 읽지 않고 바로 보낼 수 있도록 토큰과 공지 내용을 함께 싣는다.
 */
public record PushSendMessage(Long broadcastId, Long deviceId, String token, String title, String body) {
}
