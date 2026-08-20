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
-- 시연용 AS 접수 이력
--
--    목록·상세 화면이 비어 보이지 않도록 미리 넣어 둔다.
--    완료 3건 + 진행 중 2건 구성 — 710 화면의 전체/진행중/완료 탭이 모두 살아난다.
--
--    날짜는 실행 시점 기준 상대값으로 만든다.
--    고정값을 박으면 시연 날짜에 따라 "3년 전 접수"처럼 보인다.
-- ---------------------------------------------------------------------
SET @mid = (SELECT member_id FROM member WHERE email = 'user@example.com');
SET @yr  = YEAR(CURDATE());

-- ── 재실행 대비 정리 ────────────────────────────────────────
--
-- 시드는 몇 번을 돌려도 같은 결과가 나와야 한다.
-- 한 번 실패하면 중간 상태로 남아 DB 를 통째로 비워야 하는 상황을 막는다.
--
-- 시연 데이터만 지운다. 사용자가 실제로 만든 접수 건은 건드리지 않는다.
-- 대상은 아래 5개 번호로 한정한다.
CREATE TEMPORARY TABLE IF NOT EXISTS demo_as (as_id BIGINT PRIMARY KEY);
TRUNCATE demo_as;

INSERT INTO demo_as
SELECT as_id FROM as_case
WHERE as_no IN (
    CONCAT('AS-', @yr, '-00101') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00102') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00103') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00104') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00105') COLLATE utf8mb4_general_ci
);

-- FK 를 거스르지 않도록 자식부터 지운다
DELETE FROM estimate_item   WHERE estimate_id IN (SELECT estimate_id FROM estimate WHERE as_id IN (SELECT as_id FROM demo_as));
DELETE FROM estimate        WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM handover_photo  WHERE handover_id IN (SELECT handover_id FROM handover WHERE pickup_id IN (SELECT pickup_id FROM pickup WHERE as_id IN (SELECT as_id FROM demo_as)));
DELETE FROM handover        WHERE pickup_id IN (SELECT pickup_id FROM pickup WHERE as_id IN (SELECT as_id FROM demo_as));
DELETE FROM pickup          WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_photo        WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_status_history WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_case         WHERE as_id IN (SELECT as_id FROM demo_as);

-- IGNORE 를 쓰지 않는다. FK 위반 등으로 조용히 건너뛰면 시연 데이터가 비어도 알 수 없다.
INSERT INTO as_case
    (as_no, member_id, warranty_no, product_type, model_name, purchased_at, purchase_channel,
     damage_part, damage_type, damage_description, status, status_updated_at, status_message,
     expected_completed_at, completed_at, intake_type, current_location, location_type, location_status,
     created_at, updated_at)
VALUES
    -- 완료 3건
    (CONCAT('AS-', @yr, '-00101'), @mid, 'MCM-W-2022-0301', 'BAG', 'MCM 스타크 백팩 미디엄',
     '2023-04-15', 'OFFICIAL_STORE', '숄더 스트랩 연결부', 'STITCHING', '스트랩 봉제선이 뜯어졌습니다',
     'COMPLETED', NOW() - INTERVAL 40 DAY, '수선이 완료되어 고객님께 전달되었습니다',
     CURDATE() - INTERVAL 40 DAY, CURDATE() - INTERVAL 40 DAY,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 완료',
     NOW() - INTERVAL 62 DAY, NOW() - INTERVAL 40 DAY),

    (CONCAT('AS-', @yr, '-00102'), @mid, 'MCM-W-2025-1001', 'WALLET', 'MCM 비세토스 장지갑',
     '2024-01-20', 'DEPARTMENT_STORE', '카드 슬롯', 'SCRATCH', '카드 슬롯 안쪽이 긁혔습니다',
     'COMPLETED', NOW() - INTERVAL 25 DAY, '수선이 완료되어 고객님께 전달되었습니다',
     CURDATE() - INTERVAL 25 DAY, CURDATE() - INTERVAL 25 DAY,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 완료',
     NOW() - INTERVAL 45 DAY, NOW() - INTERVAL 25 DAY),

    (CONCAT('AS-', @yr, '-00103'), @mid, NULL, 'BAG', 'MCM 밀라 미니 크로스백',
     '2023-11-05', 'ONLINE_STORE', '금속 체인', 'METAL_PART', '체인 도금이 벗겨졌습니다',
     'COMPLETED', NOW() - INTERVAL 12 DAY, '수선이 완료되어 고객님께 전달되었습니다',
     CURDATE() - INTERVAL 12 DAY, CURDATE() - INTERVAL 12 DAY,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 완료',
     NOW() - INTERVAL 33 DAY, NOW() - INTERVAL 12 DAY),

    -- 진행 중 2건
    (CONCAT('AS-', @yr, '-00104'), @mid, 'MCM-W-2026-0110', 'BAG', 'MCM 클래식 백팩 미디엄',
     '2024-06-10', 'OFFICIAL_STORE', '지퍼 슬라이더', 'METAL_PART', '지퍼가 중간에 걸립니다',
     'REPAIRING', NOW() - INTERVAL 2 DAY, '수선 작업을 진행하고 있습니다',
     CURDATE() + INTERVAL 6 DAY, NULL,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 작업 중',
     NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 2 DAY),

    (CONCAT('AS-', @yr, '-00105'), @mid, NULL, 'BAG', 'MCM 로엔 카메라백',
     '2024-09-01', 'DUTY_FREE', '가죽 표면', 'DISCOLOR', '앞면 가죽이 변색되었습니다',
     'INSPECTING', NOW() - INTERVAL 1 DAY, '품질 검수를 진행하고 있습니다',
     CURDATE() + INTERVAL 3 DAY, NULL,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '품질 검수 중',
     NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 1 DAY);

-- 방금 넣은 5건의 as_id 범위. 문자열 비교를 피한다 —
-- CONCAT 결과와 컬럼의 collation 이 다르면 LIKE 가 "Illegal mix of collations" 로 실패한다.
SET @demo_first = LAST_INSERT_ID();
SET @demo_last  = @demo_first + 4;


-- 진행 이력 — 타임라인 화면이 단계별로 채워지도록
-- 완료 건은 7단계 전부, 진행 중 건은 현재 단계까지만
INSERT INTO as_status_history (as_id, status, description, occurred_at)
SELECT a.as_id, s.status, s.description,
       a.created_at + INTERVAL s.offset_day DAY
FROM as_case a
CROSS JOIN (
    SELECT 'PICKED_UP' status, '기사 인계 후 수선 센터로 이동' description, 1 offset_day, 1 ord
    UNION ALL SELECT 'RECEIVED',   '수선 센터 입고 및 실물 진단 시작', 2,  2
    UNION ALL SELECT 'DIAGNOSED',  '진단 결과에 따라 수선 범위 확정',   4,  3
    UNION ALL SELECT 'REPAIRING',  '확정된 범위로 수선 작업 진행',     6,  4
    UNION ALL SELECT 'INSPECTING', '수선 완료 후 품질 기준 최종 점검',  14, 5
    UNION ALL SELECT 'SHIPPING',   '검수 완료 후 고객 배송 진행',      18, 6
    UNION ALL SELECT 'COMPLETED',  '수선 완료',                     20, 7
) s
WHERE a.as_id BETWEEN @demo_first AND @demo_last
  AND s.ord <= CASE a.status
        WHEN 'COMPLETED'  THEN 7
        WHEN 'SHIPPING'   THEN 6
        WHEN 'INSPECTING' THEN 5
        WHEN 'REPAIRING'  THEN 4
        WHEN 'DIAGNOSED'  THEN 3
        WHEN 'RECEIVED'   THEN 2
        ELSE 1
      END;


-- ---------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------
SELECT 'driver'      AS table_name, COUNT(*) AS cnt FROM driver
UNION ALL SELECT 'member',      COUNT(*) FROM member
UNION ALL SELECT 'product',     COUNT(*) FROM product
UNION ALL SELECT 'pickup_slot', COUNT(*) FROM pickup_slot
UNION ALL SELECT 'as_case',     COUNT(*) FROM as_case
UNION ALL SELECT 'as_history',  COUNT(*) FROM as_status_history;
