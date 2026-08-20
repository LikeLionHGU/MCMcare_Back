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
     '2023-04-15', 'OFFICIAL_STORE',   24, '2025-04-15', '제조 결함 한정'),

    -- 시연 접수와 짝을 맞춘 캐리어 보증서.
    -- 보증서를 참조하는 접수 건은 제품 종류·모델명이 일치해야 화면이 어색하지 않다.
    ('MCM-W-2022-0301C', 'LUGGAGE', 'MCM 비세토스 캐리어 스몰',
     '2022-11-30', 'DUTY_FREE',        24, '2024-11-30', '제조 결함 한정'),

    ('MCM-W-2026-0110C', 'LUGGAGE', 'MCM 소프트쉘 캐리어 미디엄',
     '2025-01-08', 'ONLINE_STORE',     24, '2027-01-08', '제조 결함 한정'),

    -- 시연 영상용. 보증서 번호만 외워서 입력하면 나머지가 자동으로 채워진다.
    -- 보증이 살아 있어야 "부분 보증 적용 가능성 있음" 판정까지 보여줄 수 있어
    -- 구매일을 최근으로 잡는다. 날짜는 실행 시점 기준 상대값이다.
    ('20091123', 'LUGGAGE', 'MCM 스타크 비세토스 캐리어 미디엄',
     DATE_SUB(CURDATE(), INTERVAL 8 MONTH), 'OFFICIAL_STORE',
     24, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 8 MONTH), INTERVAL 24 MONTH), '제조 결함 한정');


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
--    접수 8건. 목록·상세 화면의 모든 단계를 하나씩 보여준다.
--
--    ESTIMATED(접수중)는 넣지 않는다 — 견적만 보고 나간 상태라
--    목록에 노출되지 않고(AsStatus.isHidden), 1시간 뒤 자동 취소된다.
--    사진은 uploads/demo/as/demo-1..9.jpg 를 쓴다 (seed-mcm.sh 가 복사).
--
--    초기 상태일수록 최근 접수다 — 실제 흐름과 맞아야 자연스럽다.
--    날짜는 실행 시점 기준 상대값이라 언제 시연해도 어색하지 않다.
--
--    재실행 안전: 아래 8개 번호에 딸린 데이터를 먼저 지우고 다시 넣는다.
-- ---------------------------------------------------------------------
SET @mid = (SELECT member_id FROM member WHERE email = 'user@example.com');
SET @yr  = YEAR(CURDATE());
-- 사진 URL 의 앞부분. 서버 배포 시에는 FILE_BASE_URL 과 같아야 화면에 이미지가 뜬다.
--   로컬   http://localhost:8080/files
--   서버   https://mcm-api.likepigs.shop/files
--
-- seed-mcm.sh 가 .env 의 FILE_BASE_URL 로 이 값을 바꿔 실행한다.
-- 직접 돌릴 때는 아래 값을 환경에 맞게 고친다.
SET @base = 'http://localhost:8080/files';
SET @img  = CONCAT(@base, '/demo/as/');

-- ── 재실행 대비 정리 ────────────────────────────────────────
CREATE TEMPORARY TABLE IF NOT EXISTS demo_as (as_id BIGINT PRIMARY KEY);
TRUNCATE demo_as;

INSERT INTO demo_as
SELECT as_id FROM as_case
WHERE as_no IN (
    CONCAT('AS-', @yr, '-00102') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00103') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00104') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00105') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00106') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00107') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00108') COLLATE utf8mb4_general_ci,
    CONCAT('AS-', @yr, '-00109') COLLATE utf8mb4_general_ci
);

-- FK 를 거스르지 않도록 자식부터 지운다
DELETE FROM estimate_item     WHERE estimate_id IN (SELECT estimate_id FROM estimate WHERE as_id IN (SELECT as_id FROM demo_as));
DELETE FROM estimate          WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM handover_photo    WHERE handover_id IN (SELECT handover_id FROM handover WHERE pickup_id IN (SELECT pickup_id FROM pickup WHERE as_id IN (SELECT as_id FROM demo_as)));
DELETE FROM handover          WHERE pickup_id IN (SELECT pickup_id FROM pickup WHERE as_id IN (SELECT as_id FROM demo_as));
DELETE FROM pickup            WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_photo          WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_status_history WHERE as_id IN (SELECT as_id FROM demo_as);
DELETE FROM as_case           WHERE as_id IN (SELECT as_id FROM demo_as);


-- ── 접수 8건 ────────────────────────────────────────────────
-- IGNORE 를 쓰지 않는다. 조용히 건너뛰면 시연 데이터가 비어도 알 수 없다.
INSERT INTO as_case
    (as_no, member_id, warranty_no, product_type, model_name, purchased_at, purchase_channel,
     damage_part, damage_type, damage_description, status, status_updated_at, status_message,
     expected_completed_at, completed_at, intake_type, current_location, location_type, location_status,
     created_at, updated_at)
VALUES
    -- 2. 접수완료 — 픽업 예약까지 마침
    (CONCAT('AS-', @yr, '-00102'), @mid, NULL, 'LUGGAGE', 'MCM 트래블 캐리어 라지',
     '2024-08-22', 'ONLINE_STORE', '측면 하단', 'ETC', '모서리가 찢어져 내용물이 보입니다',
     'PICKUP_BOOKED', NOW() - INTERVAL 20 HOUR, '기사 방문 예정입니다',
     NULL, NULL, '픽업 수거 접수', NULL, NULL, NULL,
     NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 20 HOUR),

    -- 3. 픽업완료 — 기사 인계 직후
    (CONCAT('AS-', @yr, '-00103'), @mid, NULL, 'LUGGAGE', 'MCM 하드쉘 캐리어 미디엄',
     '2024-05-10', 'DEPARTMENT_STORE', '측면 패널', 'ETC', '충격으로 외피가 뚫렸습니다',
     'PICKED_UP', NOW() - INTERVAL 2 DAY, '수선 센터로 이동 중입니다',
     CURDATE() + INTERVAL 12 DAY, NULL, '픽업 수거 접수', '이동 중', '국내', '수선 센터 이동 중',
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),

    -- 4. 손상부위 진단중 — 센터 입고
    (CONCAT('AS-', @yr, '-00104'), @mid, 'MCM-W-2022-0301C', 'LUGGAGE', 'MCM 비세토스 캐리어 스몰',
     '2022-11-30', 'DUTY_FREE', '전면 패널', 'ETC', '날카로운 물체에 베인 자국이 있습니다',
     'RECEIVED', NOW() - INTERVAL 4 DAY, '실물 진단을 진행하고 있습니다',
     CURDATE() + INTERVAL 10 DAY, NULL, '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '실물 진단 중',
     NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 4 DAY),

    -- 5. 손상부위 진단완료 — 수선 범위 확정
    (CONCAT('AS-', @yr, '-00105'), @mid, NULL, 'LUGGAGE', 'MCM 알루미늄 캐리어 라지',
     '2023-07-18', 'OFFICIAL_STORE', '상단 모서리', 'ETC', '여러 곳이 파손되어 내용물이 노출됩니다',
     'DIAGNOSED', NOW() - INTERVAL 5 DAY, '수선 범위가 확정되었습니다',
     CURDATE() + INTERVAL 9 DAY, NULL, '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 대기',
     NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 5 DAY),

    -- 6. 수선중
    (CONCAT('AS-', @yr, '-00106'), @mid, 'MCM-W-2026-0110C', 'LUGGAGE', 'MCM 소프트쉘 캐리어 미디엄',
     '2025-01-08', 'ONLINE_STORE', '측면 원단', 'ETC', '원단이 찢어져 구멍이 났습니다',
     'REPAIRING', NOW() - INTERVAL 3 DAY, '수선 작업을 진행하고 있습니다',
     CURDATE() + INTERVAL 6 DAY, NULL, '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 작업 중',
     NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 3 DAY),

    -- 7. 검수중
    (CONCAT('AS-', @yr, '-00107'), @mid, NULL, 'LUGGAGE', 'MCM 클래식 캐리어 라지',
     '2022-04-02', 'DEPARTMENT_STORE', '본체 전면', 'ETC', '전체적으로 심하게 파손되었습니다',
     'INSPECTING', NOW() - INTERVAL 1 DAY, '품질 검수를 진행하고 있습니다',
     CURDATE() + INTERVAL 3 DAY, NULL, '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '품질 검수 중',
     NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 1 DAY),

    -- 8. 발송중
    (CONCAT('AS-', @yr, '-00108'), @mid, NULL, 'LUGGAGE', 'MCM 다이아몬드 캐리어 미디엄',
     '2024-02-26', 'OFFICIAL_STORE', '손잡이', 'METAL_PART', '손잡이 고정부가 흔들립니다',
     'SHIPPING', NOW() - INTERVAL 6 HOUR, '고객님께 배송 중입니다',
     CURDATE() + INTERVAL 1 DAY, NULL, '픽업 수거 접수', '배송 중', '국내', '고객 배송 중',
     NOW() - INTERVAL 26 DAY, NOW() - INTERVAL 6 HOUR),

    -- 9. 완료
    (CONCAT('AS-', @yr, '-00109'), @mid, NULL, 'LUGGAGE', 'MCM 파스텔 캐리어 스몰',
     '2023-09-12', 'ONLINE_STORE', '전면 하단', 'SCRATCH', '표면에 긁힌 자국이 있습니다',
     'COMPLETED', NOW() - INTERVAL 8 DAY, '수선이 완료되어 고객님께 전달되었습니다',
     CURDATE() - INTERVAL 8 DAY, CURDATE() - INTERVAL 8 DAY,
     '픽업 수거 접수', 'MCM 서울 수선 센터', '국내', '수선 완료',
     NOW() - INTERVAL 35 DAY, NOW() - INTERVAL 8 DAY);

-- 방금 넣은 8건의 as_id 범위. 문자열 비교를 피한다 —
-- CONCAT 결과와 컬럼의 collation 이 다르면 LIKE 가 "Illegal mix of collations" 로 실패한다.
SET @demo_first = LAST_INSERT_ID();
SET @demo_last  = @demo_first + 7;


-- ── 손상 사진 ───────────────────────────────────────────────
-- 접수 순서대로 demo-1..9.jpg 를 붙인다. 목록 썸네일과 상세 화면에 쓰인다.
INSERT INTO as_photo (as_id, file_url, photo_type, sort_order, created_at)
SELECT a.as_id,
       CONCAT(@img, 'demo-', a.as_id - @demo_first + 1, '.jpg'),
       'DAMAGE', 0, a.created_at
FROM as_case a
WHERE a.as_id BETWEEN @demo_first AND @demo_last;


-- ── 진행 이력 ───────────────────────────────────────────────
-- 현재 상태까지의 단계만 남긴다. 아직 오지 않은 단계는 화면이 "예정"으로 채운다.
INSERT INTO as_status_history (as_id, status, description, occurred_at)
SELECT a.as_id, s.status, s.description,
       a.created_at + INTERVAL s.offset_hour HOUR
FROM as_case a
CROSS JOIN (
    SELECT 'PICKED_UP'  status, '기사 인계 후 수선 센터로 이동' description, 24  offset_hour, 1 ord
    UNION ALL SELECT 'RECEIVED',   '수선 센터 입고 및 실물 진단 시작', 48,  2
    UNION ALL SELECT 'DIAGNOSED',  '진단 결과에 따라 수선 범위 확정',   96,  3
    UNION ALL SELECT 'REPAIRING',  '확정된 범위로 수선 작업 진행',     144, 4
    UNION ALL SELECT 'INSPECTING', '수선 완료 후 품질 기준 최종 점검',  336, 5
    UNION ALL SELECT 'SHIPPING',   '검수 완료 후 고객 배송 진행',      432, 6
    UNION ALL SELECT 'COMPLETED',  '수선 완료',                     480, 7
) s
WHERE a.as_id BETWEEN @demo_first AND @demo_last
  AND s.ord <= CASE a.status
        WHEN 'COMPLETED'  THEN 7
        WHEN 'SHIPPING'   THEN 6
        WHEN 'INSPECTING' THEN 5
        WHEN 'REPAIRING'  THEN 4
        WHEN 'DIAGNOSED'  THEN 3
        WHEN 'RECEIVED'   THEN 2
        WHEN 'PICKED_UP'  THEN 1
        ELSE 0
      END;


-- ---------------------------------------------------------------------
-- 확인
-- ---------------------------------------------------------------------
SELECT 'driver'      AS table_name, COUNT(*) AS cnt FROM driver
UNION ALL SELECT 'member',      COUNT(*) FROM member
UNION ALL SELECT 'product',     COUNT(*) FROM product
UNION ALL SELECT 'pickup_slot', COUNT(*) FROM pickup_slot
UNION ALL SELECT 'as_case',     COUNT(*) FROM as_case
UNION ALL SELECT 'as_history',  COUNT(*) FROM as_status_history
UNION ALL SELECT 'as_photo',    COUNT(*) FROM as_photo;
