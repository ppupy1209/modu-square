-- 로컬 검증용 푸시 수신 기기 10,000대.
-- 외부 푸시 업체 대역(infra/push-provider-mock)은 토큰 접두사로 실패를 재현한다.
--   expired-*: 앱 삭제 등으로 만료된 토큰. 업체가 410을 돌려주며 다시 보내도 성공하지 않는다.
--   stuck-*  : 업체 쪽 특정 구간 장애로 계속 503을 돌려주는 토큰.
--   device-* : 정상 토큰. 업체가 일정 확률로 일시적인 503을 돌려준다.
SET SESSION cte_max_recursion_depth = 20000;

INSERT IGNORE INTO push.push_device (device_id, member_id, token, enabled, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
  n,
  n,
  CONCAT(
    CASE
      WHEN n % 100 = 0 THEN 'expired-'
      WHEN n % 100 = 50 THEN 'stuck-'
      ELSE 'device-'
    END,
    n
  ),
  1,
  NOW(6)
FROM seq;
