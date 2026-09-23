CREATE DATABASE IF NOT EXISTS push
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 푸시 수신에 동의한 기기. 발송 대상은 회원이 아니라 기기 토큰 단위다.
CREATE TABLE IF NOT EXISTS push.push_device (
  device_id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  token VARCHAR(255) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (device_id),
  UNIQUE KEY uk_push_device_token (token),
  KEY idx_push_device_enabled (enabled, device_id)
) ENGINE=InnoDB;

-- 운영자가 등록한 공지 발송 요청.
-- idempotency_key는 같은 요청이 두 번 접수되지 않도록 클라이언트가 보내는 키다.
CREATE TABLE IF NOT EXISTS push.push_broadcast (
  broadcast_id BIGINT NOT NULL AUTO_INCREMENT,
  idempotency_key VARCHAR(64) NULL,
  title VARCHAR(100) NOT NULL,
  body VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL,
  target_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (broadcast_id),
  UNIQUE KEY uk_push_broadcast_idempotency_key (idempotency_key),
  KEY idx_push_broadcast_status (status, broadcast_id)
) ENGINE=InnoDB;

-- 공지 하나를 기기 하나에 보내는 발송 단위.
-- (broadcast_id, device_id)가 한 번만 발송돼야 하는 단위이자 중복 발송을 막는 기준이다.
CREATE TABLE IF NOT EXISTS push.push_delivery (
  broadcast_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  token VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  failure_reason VARCHAR(40) NULL,
  sent_at DATETIME(6) NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (broadcast_id, device_id)
) ENGINE=InnoDB;
