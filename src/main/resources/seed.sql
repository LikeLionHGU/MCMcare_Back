-- =====================================================================
-- mcmcare 시드 데이터
--
-- 실행 시점: 애플리케이션을 한 번 기동해 테이블이 생성된 뒤 (ddl-auto: update)
-- 재실행 안전: 전부 INSERT IGNORE / 조건부 INSERT 라 중복 실행해도 문제없다
--
-- 테스트 계정
--   고객  user@example.com / Password123!
--   기사  driver01         / Driver123!
-- =====================================================================

USE mcmcare;

-- ---------------------------------------------------------------------
-- 1. 기사 — 없으면 픽업 예약이 422 NO_AVAILABLE_DRIVER 로 막힌다
-- ---------------------------------------------------------------------
INSERT IGNORE INTO driver
    (login_id, password, name, phone, vehicle_no, verify_no, affiliation, active, created_at)
VALUES
    ('driver01',
     '$2a$10$msgcKPWPWV5C8BQGsy8wOOgx9SqQ8MeKpfMYkYy11F/ItoqU93cgm',   -- Driver123!
     '김민준', '01098765432', '12가 3456', 'DV-2026-0031',
     'MCM 공식 파트너 수거 기사', TRUE, NOW());


-- ---------------------------------------------------------------------
-- 2. 테스트 고객 — 시연 시 회원가입 화면을 거치지 않고 바로 로그인할 때 사용
-- ---------------------------------------------------------------------
INSERT IGNORE INTO member
    (email, password, name, phone, birth_date,
     agreed_service, agreed_privacy, agreed_at, provider, created_at, updated_at)
VALUES
    ('user@example.com',
     '$2a$10$ZhZ0t0tva3wQ8XGaffS0IukrVQLYWBNaW26HQoZQxUlJz3ouwDOu6',   -- Password123!
     '이서연', '01012345678', '1998-03-15',
     TRUE, TRUE, NOW(), 'LOCAL', NOW(), NOW());

INSERT INTO marketing_consent (member_id, agreed, occurred_at)
SELECT m.member_id, FALSE, NOW()
FROM member m
WHERE m.email = 'user@example.com'
  AND NOT EXISTS (SELECT 1 FROM marketing_consent c WHERE c.member_id = m.member_id);


-- ---------------------------------------------------------------------
-- 3. 제품(보증서) — 715 자동 채움 · 712 보증 판정에 사용
--
--    보증 판정 3가지 경우를 모두 확인할 수 있도록 구성했다.
--      MCM-W-2025-1001  보증 유효 + 봉제손상 → PARTIAL
--      MCM-W-2022-0301  보증 만료          → NOT_COVERED
--      MCM-W-2026-0110  보증 유효 + 그 외    → NOT_COVERED
--    보증서 없이 접수하면 UNKNOWN 이 된다.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO product
    (warranty_no, product_type, model_name,
     purchased_at, purchase_channel, warranty_months, warranty_expires_at, warranty_scope)
VALUES
    ('MCM-W-2025-1001', 'BAG',    'MCM 클래식 백팩 미디엄',
     '2025-10-01', 'OFFICIAL_STORE',   24, '2027-10-01', '제조 결함 한정'),

    ('MCM-W-2022-0301', 'BAG',    'MCM 스타크 백팩 미디엄',
     '2022-03-05', 'DEPARTMENT_STORE', 24, '2024-03-05', '제조 결함 한정'),

    ('MCM-W-2026-0110', 'WALLET', 'MCM 클래식 지갑',
     '2026-01-10', 'ONLINE_STORE',     24, '2028-01-10', '제조 결함 한정'),

    ('MCM-W-2024-0722', 'BAG',    'MCM 미니 크로스백',
     '2024-07-22', 'DUTY_FREE',        24, '2026-07-22', '제조 결함 한정'),

    ('MCM-W-2023-0415', 'BAG',    'MCM 비세토스 토트백',
     '2023-04-15', 'OFFICIAL_STORE',   24, '2025-04-15', '제조 결함 한정');


-- ---------------------------------------------------------------------
-- 4. 픽업 슬롯 — 없으면 720 화면의 날짜·시간 선택지가 비어 있다
--
--    오늘부터 14일 × 10:00~17:30 (점심 12:00~13:00 제외) 30분 단위
--    상대 날짜(CURDATE) 기준이므로 언제 실행해도 시연 당일이 포함된다
--
--    슬롯 단위를 2시간으로 바꾸려면 이 블록만 다시 작성하면 된다.
--    스키마와 코드는 그대로다.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO pickup_slot (slot_date, slot_start, slot_end, capacity, is_blocked)
SELECT
    CURDATE() + INTERVAL d.n DAY                     AS slot_date,
    t.st                                             AS slot_start,
    ADDTIME(t.st, '00:30:00')                        AS slot_end,
    3                                                AS capacity,
    FALSE                                            AS is_blocked
FROM
    (SELECT 0 n UNION ALL SELECT 1  UNION ALL SELECT 2  UNION ALL SELECT 3
     UNION ALL SELECT 4  UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7
     UNION ALL SELECT 8  UNION ALL SELECT 9  UNION ALL SELECT 10 UNION ALL SELECT 11
     UNION ALL SELECT 12 UNION ALL SELECT 13) d
CROSS JOIN
    (SELECT '10:00:00' st UNION ALL SELECT '10:30:00'
     UNION ALL SELECT '11:00:00' UNION ALL SELECT '11:30:00'
     UNION ALL SELECT '13:00:00' UNION ALL SELECT '13:30:00'
     UNION ALL SELECT '14:00:00' UNION ALL SELECT '14:30:00'
     UNION ALL SELECT '15:00:00' UNION ALL SELECT '15:30:00'
     UNION ALL SELECT '16:00:00' UNION ALL SELECT '16:30:00'
     UNION ALL SELECT '17:00:00' UNION ALL SELECT '17:30:00') t;


-- ---------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------
SELECT 'driver'      AS table_name, COUNT(*) AS cnt FROM driver
UNION ALL SELECT 'member',      COUNT(*) FROM member
UNION ALL SELECT 'product',     COUNT(*) FROM product
UNION ALL SELECT 'pickup_slot', COUNT(*) FROM pickup_slot;
