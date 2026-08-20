# MCM 케어 API 명세서

명품 AS 접수 · 견적 · 픽업 서비스의 백엔드 REST API 명세다. 컨트롤러 / DTO / 시큐리티 설정을 근거로 작성했다.

- **Base URL (로컬):** `http://localhost:8080`
- **성공 응답:** 각 도메인 DTO를 그대로 반환한다(공통 래퍼 없음).
- **실패 응답:** 아래 [공통 규약](#공통-규약) 참조.
- **Swagger UI:** `/swagger-ui.html` · **OpenAPI JSON:** `/v3/api-docs`

---

## 공통 규약

### 인증

세 가지 인증 방식이 존재한다.

| 방식 | 대상 | 전달 방법 |
|---|---|---|
| `bearerAuth` (JWT) | 회원(MEMBER) · 기사(DRIVER) API | `Authorization: Bearer {accessToken}` 헤더 |
| `adminKey` | 관리자 API (`/api/admin/**`) | `X-Admin-Key: {key}` 헤더 |
| 서명 URL | 파일(`/files/**`) | URL 쿼리의 만료 시각 + 서명 파라미터 |

- JWT는 로그인 계열 API 응답의 `accessToken`으로 발급된다. 유효기간은 기본 120분(`expiresIn`은 초 단위).
- 토큰에는 역할(`MEMBER` 또는 `DRIVER`)이 담기며, `@PreAuthorize`로 엔드포인트별 역할을 강제한다.
- 만료된 토큰으로 보호된 API를 호출하면 **401 `TOKEN_EXPIRED`**.
- 인증/인가 실패(토큰 없음·역할 불일치·관리자 키 불일치)는 **403 `NO_PERMISSION`**.

**인증 불필요(공개) 경로:** `/api/health`, `/api/member/signup`, `/api/member/login`, `/api/member/login/google`, `/api/driver/login`, `/files/**`, Swagger 관련 경로.

### 에러 응답 본문

```json
{
  "code": "ERROR_CODE_NAME",
  "message": "사용자용 메시지",
  "asNo": "AS-2026-00341"
}
```

- `code`는 아래 에러 코드 표의 상수명, `message`는 사용자용 문구.
- `asNo`는 견적 실패 등 일부 케이스에만 포함되며, `null`이면 직렬화되지 않는다(응답 전역이 `non_null`).

### 에러 코드

| HTTP | code | message |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력값을 확인해 주세요. |
| 400 | `PASSWORD_MISMATCH` | 비밀번호가 일치하지 않습니다. |
| 400 | `FUTURE_BIRTH_DATE` | 생년월일은 오늘 이후로 지정할 수 없습니다. |
| 400 | `AGREEMENT_REQUIRED` | 필수 약관에 동의해 주세요. |
| 400 | `PHOTO_REQUIRED` | 사진을 최소 1장 첨부해 주세요. |
| 400 | `TOO_MANY_PHOTOS` | 사진은 최대 4장까지 첨부할 수 있습니다. |
| 400 | `PHOTO_TYPE_REQUIRED` | 전체 제품 사진 1장과 손상 부위 사진을 최소 1장 이상 첨부해 주세요. |
| 400 | `TOO_MANY_HANDOVER_PHOTOS` | 인계 사진은 최대 5장까지 첨부할 수 있습니다. |
| 400 | `PAST_DATE` | 지난 날짜 또는 시간대는 선택할 수 없습니다. |
| 400 | `SIGN_REQUIRED` | 서명이 필요합니다. |
| 401 | `INVALID_CREDENTIALS` | 이메일, 비밀번호를 다시 한번 확인해주세요. |
| 401 | `TOKEN_EXPIRED` | 로그인이 만료되었습니다. 다시 로그인해 주세요. |
| 403 | `NO_PERMISSION` | 접근 권한이 없습니다. |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 요청 방식입니다. |
| 404 | `NO_MATCHING_DATA` | 요청한 정보를 찾을 수 없습니다. |
| 409 | `EMAIL_DUPLICATED` | 이미 사용 중인 이메일입니다. |
| 409 | `SLOT_FULL` | 선택하신 시간대는 예약이 마감되었습니다. |
| 409 | `PICKUP_ALREADY_EXISTS` | 이미 픽업 예약이 등록된 접수 건입니다. |
| 409 | `ALREADY_ESTIMATED` | 이미 견적이 산출된 접수 건입니다. |
| 409 | `ALREADY_HANDED_OVER` | 이미 인계가 완료된 건입니다. |
| 409 | `NUMBER_CONFLICT` | 요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요. |
| 422 | `CANCEL_DEADLINE_PASSED` | 픽업 예정일 24시간 전까지만 취소할 수 있습니다. |
| 422 | `INVALID_STATUS` | 현재 상태에서는 처리할 수 없습니다. |
| 422 | `NO_AVAILABLE_DRIVER` | 배정 가능한 기사가 없습니다. |
| 422 | `INVALID_PICKUP_DATE` | 픽업 예정일이 아닌 건은 처리할 수 없습니다. |
| 502 | `ESTIMATE_FAILED` | 견적 분석에 실패했습니다. 잠시 후 다시 시도해 주세요. |

### 열거형(enum) 코드

| Enum | 값 (code / label) |
|---|---|
| `ProductType` | BAG(가방), WALLET(지갑), BELT(벨트), SHOES(신발), ACCESSORY(소품), ETC(기타) |
| `DamageType` | DENT(찍힘), SCRATCH(긁힘), DISCOLOR(변색), METAL_PART(금속부품손상), STITCHING(봉제손상), ETC(기타) |
| `PurchaseChannel` | OFFICIAL_STORE(MCM 공식 매장), DEPARTMENT_STORE(백화점), DUTY_FREE(면세점), ONLINE_STORE(온라인스토어), ETC(기타) |
| `PhotoType` | PRODUCT(제품 전체), DAMAGE(손상 부위) |
| `AsStatus` | DRAFT(작성 중), ANALYZING(AI 분석 중), ESTIMATE_FAILED(견적 실패), ESTIMATED(접수중), PICKUP_BOOKED(접수완료), PICKED_UP(픽업완료), RECEIVED(손상부위 진단중), DIAGNOSED(손상부위 진단완료), REPAIRING(수선중), INSPECTING(검수중), SHIPPING(발송중), COMPLETED(완료), CANCELLED(접수 취소) |

---

## 엔드포인트 요약

| # | Method | Path | 권한 | 설명 |
|---|---|---|---|---|
| 1 | GET | `/api/health` | 공개 | 헬스체크 |
| 2 | POST | `/api/member/signup` | 공개 | 회원가입 |
| 3 | POST | `/api/member/login` | 공개 | 로그인 |
| 4 | POST | `/api/member/login/google` | 공개 | 구글 로그인 |
| 5 | GET | `/api/member/home` | MEMBER | 홈 — 최근 AS 5건 |
| 6 | GET | `/api/member/info` | MEMBER | 내 정보 조회 |
| 7 | PUT | `/api/member` | MEMBER | 정보 수정 · 마케팅 동의 변경 |
| 8 | GET | `/api/product/{warrantyNo}` | MEMBER | 보증서 번호로 제품 자동 채움 |
| 9 | GET | `/api/asCase/form` | MEMBER | 접수 폼 초기 데이터(드롭다운) |
| 10 | POST | `/api/asCase` | MEMBER | 접수 생성 + AI 견적 분석 |
| 11 | GET | `/api/asCase/estimate/{asNo}` | MEMBER | AI 예상 견적 결과 |
| 12 | POST | `/api/asCase/estimate/{asNo}` | MEMBER | 견적 재분석 |
| 13 | POST | `/api/asCase/list` | MEMBER | 나의 AS 목록 |
| 14 | GET | `/api/asCase/detail/{asNo}` | MEMBER | 나의 AS 상세 |
| 15 | GET | `/api/asCase/handover/{asNo}` | MEMBER | 인계 완료 확인 |
| 16 | DELETE | `/api/asCase/{asNo}` | MEMBER | 접수 취소 |
| 17 | GET | `/api/pickup/form/{asNo}` | MEMBER | 픽업 예약 진입 |
| 18 | POST | `/api/pickup/slot` | MEMBER | 예약 가능 슬롯 |
| 19 | POST | `/api/pickup/{asNo}` | MEMBER | 예약 확정 |
| 20 | GET | `/api/pickup/complete/{pickupNo}` | MEMBER | 예약 완료 화면 |
| 21 | GET | `/api/pickup/{pickupNo}` | MEMBER | 예약 조회 |
| 22 | DELETE | `/api/pickup/{pickupNo}` | MEMBER | 예약 취소 |
| 23 | POST | `/api/driver/login` | 공개 | 기사 로그인 |
| 24 | GET | `/api/driver/pickup/list` | DRIVER | 오늘의 수거 목록 |
| 25 | POST | `/api/driver/handover/{pickupNo}` | DRIVER | 인계 완료(사진·서명 업로드) |
| 26 | PUT | `/api/admin/asCase/{asNo}/status` | adminKey | AS 상태 변경 |
| 27 | POST | `/api/chat` | MEMBER | AI 상담 |
| 28 | GET | `/api/chat/opening` | MEMBER | 상담 시작 인사말 |
| 29 | POST | `/api/chat/stream` | MEMBER | AI 상담(SSE 스트리밍) |

---

## 1. 헬스체크

### `GET /api/health`
- **권한:** 공개
- **응답 200:** `{ "status": "UP" }`

---

## 회원 (Member) — `/api/member`

### 2. `POST /api/member/signup` — 회원가입
- **권한:** 공개
- **Request Body** (`application/json`):

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `email` | string | ✔ | 이메일 형식, 최대 50자 |
| `password` | string | ✔ | 최대 50자 |
| `passwordConfirm` | string | ✔ | `password`와 일치해야 함 |
| `name` | string | ✔ | 최대 30자 |
| `phone` | string | ✔ | 숫자만, 최대 50자 |
| `birthDate` | date (`YYYY-MM-DD`) | ✔ | 오늘 이후 불가 |
| `agreedService` | boolean | | 서비스 이용약관 동의(필수 약관) |
| `agreedPrivacy` | boolean | | 개인정보 처리방침 동의(필수 약관) |
| `agreedMarketing` | boolean | | 마케팅 수신 동의 |

- **응답 200:** `{ "memberId": 1 }`
- **주요 에러:** `VALIDATION_FAILED`, `PASSWORD_MISMATCH`, `FUTURE_BIRTH_DATE`, `AGREEMENT_REQUIRED`, `EMAIL_DUPLICATED`

### 3. `POST /api/member/login` — 로그인
- **권한:** 공개
- **Request Body:** `{ "email": string(필수), "password": string(필수) }`
- **응답 200:**
```json
{ "accessToken": "eyJ...", "expiresIn": 7200, "memberId": 1, "name": "홍길동" }
```
- **주요 에러:** `INVALID_CREDENTIALS`

### 4. `POST /api/member/login/google` — 구글 로그인
- **권한:** 공개
- **Request Body:** `{ "idToken": string(필수) }` — 구글 SDK가 발급한 ID 토큰(JWT)
- **응답 200:**
```json
{
  "accessToken": "eyJ...",
  "expiresIn": 7200,
  "memberId": 1,
  "name": "홍길동",
  "newMember": true,
  "needsContactInfo": true
}
```
  - `newMember`: 이번 호출로 새로 가입된 회원인지 여부
  - `needsContactInfo`: 소셜 가입 회원이 아직 연락처를 입력하지 않았는지 여부

### 5. `GET /api/member/home` — 홈(최근 AS 5건)
- **권한:** MEMBER
- **응답 200:**
```json
{
  "asCaseList": [
    {
      "asNo": "AS-2026-00341",
      "modelName": "모델명",
      "status": "REPAIRING",
      "statusLabel": "수선중",
      "expectedCompletedAt": "2026-08-30",
      "completedAt": null
    }
  ]
}
```

### 6. `GET /api/member/info` — 내 정보 조회
- **권한:** MEMBER
- **응답 200:**
```json
{
  "memberId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "phone": "01012345678",
  "birthDate": "1990-01-01",
  "agreedMarketing": false
}
```

### 7. `PUT /api/member` — 정보 수정 · 마케팅 동의 변경
- **권한:** MEMBER
- **Request Body:**

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `name` | string | ✔ | 최대 30자 |
| `phone` | string | ✔ | 숫자만, 최대 50자 |
| `agreedMarketing` | boolean | | 마케팅 수신 동의 여부 |

- **응답 200:** `{ "memberId": 1 }`

---

## 제품 (Product) — `/api/product`

### 8. `GET /api/product/{warrantyNo}` — 보증서 자동 채움
- **권한:** MEMBER
- **Path:** `warrantyNo` — 보증서 번호
- **응답 200:**
```json
{
  "warrantyNo": "12CO001",
  "productType": "BAG",
  "productTypeLabel": "가방",
  "modelName": "모델명",
  "purchasedAt": "2024-01-01",
  "warrantyMonths": 24,
  "warrantyExpiresAt": "2026-01-01",
  "warrantyScope": "보증 범위 설명"
}
```
  - 구매처(`purchaseChannel`)는 반환하지 않는다(브랜드 보증 조회로 확인 불가).
- **주요 에러:** `NO_MATCHING_DATA`

---

## AS 접수 (AsCase) — `/api/asCase`

### 9. `GET /api/asCase/form` — 접수 폼 초기 데이터
- **권한:** MEMBER
- **응답 200:** 드롭다운 코드 목록
```json
{
  "productTypeList":     [ { "code": "BAG", "label": "가방" } ],
  "purchaseChannelList": [ { "code": "OFFICIAL_STORE", "label": "MCM 공식 매장" } ],
  "damageTypeList":      [ { "code": "SCRATCH", "label": "긁힘" } ]
}
```

### 10. `POST /api/asCase` — 접수 생성 + AI 견적 분석
- **권한:** MEMBER
- **Content-Type:** `multipart/form-data`
- **Parts:**
  - `request` (JSON): 아래 필드
  - `images` (파일 배열): 1~4장. `request.photoTypeList`와 순서 1:1 대응.

  `request` 필드:

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `warrantyNo` | string | | 선택 입력 |
| `productType` | ProductType | ✔ | |
| `modelName` | string | ✔ | 최대 50자 |
| `purchasedAt` | date | | |
| `purchaseChannel` | PurchaseChannel | | |
| `damagePart` | string | ✔ | 최대 50자 |
| `damageType` | DamageType | ✔ | |
| `damageDescription` | string | | 최대 200자 |
| `photoTypeList` | PhotoType[] | ✔ | 1~4개, `images`와 순서 대응. PRODUCT·DAMAGE 조합 필수(첫 장 PRODUCT, 나머지 DAMAGE 권장) |

- **응답 200:** `{ "asNo": "AS-2026-00341" }`
- **주요 에러:** `VALIDATION_FAILED`, `PHOTO_REQUIRED`, `TOO_MANY_PHOTOS`, `PHOTO_TYPE_REQUIRED`, `ESTIMATE_FAILED`(502, 본문에 `asNo` 포함), `NUMBER_CONFLICT`

### 11. `GET /api/asCase/estimate/{asNo}` — AI 예상 견적 결과
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:**
```json
{
  "asNo": "AS-2026-00341",
  "status": "ESTIMATED",
  "statusLabel": "접수중",
  "modelName": "모델명",
  "damagePart": "손잡이",
  "photoUrlList": ["https://.../files/...?expires=...&signature=..."],
  "damageCategory": "SCRATCH",
  "damageSeverity": "MINOR",
  "confidenceGrade": "A",
  "confidenceNote": "신뢰도 설명",
  "itemList": [
    { "repairItemName": "가죽 보수", "estimatedPrice": 120000, "minPrice": 100000, "maxPrice": 150000 }
  ],
  "totalEstimatedPrice": 120000,
  "totalMinPrice": 100000,
  "totalMaxPrice": 150000,
  "noDamageNotice": null,
  "purchasedAt": "2024-01-01",
  "warrantyMonths": 24,
  "warrantyScope": "보증 범위",
  "warrantyVerdict": "COVERED",
  "warrantyVerdictLabel": "보증 대상",
  "warrantyNoteList": ["안내 문구"]
}
```
  - `noDamageNotice`에 값이 있으면 AI가 손상을 탐지하지 못한 경우로, 화면은 비용 대신 이 문구를 표시한다.
  - 금액 단위는 원(KRW). 파일 URL은 만료 시각·서명이 붙은 서명 URL이다.
- **참고:** DRAFT · ANALYZING · ESTIMATE_FAILED · CANCELLED 상태에서는 조회 불가(재분석 API 사용).

### 12. `POST /api/asCase/estimate/{asNo}` — 견적 재분석
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:** `{ "asNo": "AS-2026-00341", "status": "ESTIMATED" }`
- **주요 에러:** `ALREADY_ESTIMATED`, `ESTIMATE_FAILED`

### 13. `POST /api/asCase/list` — 나의 AS 목록
- **권한:** MEMBER
- **Request Body:**

| 필드 | 타입 | 필수 | 기본값 |
|---|---|---|---|
| `filter` | string | | `ALL` (`ALL` · `IN_PROGRESS` · `COMPLETED`) |
| `page` | int | | 0 (음수는 0으로 보정) |
| `size` | int | | 20 (최대 100) |

- **응답 200:**
```json
{
  "inProgressCount": 2,
  "completedCount": 5,
  "lastUpdatedAt": "2026-08-18",
  "itemList": [
    {
      "asNo": "AS-2026-00341",
      "modelName": "모델명",
      "thumbnailUrl": "https://.../files/...?expires=...&signature=...",
      "createdAt": "2026-08-01",
      "status": "REPAIRING",
      "statusLabel": "수선중",
      "expectedCompletedAt": "2026-08-30",
      "completedAt": null,
      "statusUpdatedAt": "2026-08-15T10:00:00"
    }
  ],
  "totalPages": 1,
  "totalElements": 7
}
```

### 14. `GET /api/asCase/detail/{asNo}` — 나의 AS 상세
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:**
```json
{
  "asNo": "AS-2026-00341",
  "modelName": "모델명",
  "createdAt": "2026-08-01",
  "intakeType": "PICKUP",
  "pickupNo": "PKP-2026-000847",
  "photoUrlList": ["https://.../files/...?..."],
  "damagePart": "손잡이",
  "damageType": "SCRATCH",
  "damageTypeLabel": "긁힘",
  "damageDescription": "설명",
  "damageCategory": "SCRATCH",
  "status": "REPAIRING",
  "statusLabel": "수선중",
  "statusUpdatedAt": "2026-08-15T10:00:00",
  "statusMessage": "수선 진행 중입니다.",
  "expectedCompletedAt": "2026-08-30",
  "expectedUpdatedAt": "2026-08-10",
  "delayReason": null,
  "currentLocation": "수선 센터",
  "locationType": "REPAIR_CENTER",
  "locationStatus": "IN_REPAIR",
  "historyList": [
    {
      "status": "PICKED_UP",
      "statusLabel": "픽업완료",
      "completed": true,
      "occurredAt": "2026-08-05",
      "description": "기사 인계 완료",
    }
  ]
}
```
  - `historyList`는 발생한 이력과 예정 단계를 함께 담는다. `completed=false` 항목은 "예정 · {description}"으로 표시된다.
  - `damageType`/`damageTypeLabel`은 고객 진술, `damageCategory`는 AI 판정(견적 없으면 null)이다.

### 15. `GET /api/asCase/handover/{asNo}` — 인계 완료 확인
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:**
```json
{
  "asNo": "AS-2026-00341",
  "modelName": "모델명",
  "handedOverAt": "2026-08-05T14:00:00",
  "photoUrlList": ["https://.../files/...?..."],
  "customerSignUrl": "https://.../files/...?...",
  "driverSignUrl": "https://.../files/...?...",
  "driverName": "기사명",
  "driverAffiliation": "소속",
  "driverVerifyNo": "인증번호",
  "insuranceApplied": true,
  "insuranceLimit": 5000000
}
```
  - 기사 정보는 인계 시점의 스냅샷이다.
- **주요 에러:** `NO_MATCHING_DATA`(인계 기록 없음)

### 16. `DELETE /api/asCase/{asNo}` — 접수 취소
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:** `{ "asNo": "AS-2026-00341" }`
- **주요 에러:** `INVALID_STATUS`

---

## 픽업 (Pickup) — `/api/pickup`

### 17. `GET /api/pickup/form/{asNo}` — 픽업 예약 진입
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **응답 200:**
```json
{
  "asNo": "AS-2026-00341",
  "modelName": "모델명",
  "statusLabel": "접수중",
  "photoUrl": "https://.../files/...?...",
  "phone": "01012345678"
}
```

### 18. `POST /api/pickup/slot` — 예약 가능 슬롯
- **권한:** MEMBER
- **Request Body:** `{ "startDate": "2026-08-18", "endDate": "2026-09-01" }`
  - 둘 다 선택. `startDate` 미지정 시 오늘, `endDate` 미지정 시 시작일 +14일.
- **응답 200:**
```json
{
  "dateList": [
    {
      "date": "2026-08-18",
      "slotList": [
        { "slotStart": "10:00:00", "slotEnd": "12:00:00", "available": true }
      ]
    }
  ]
}
```

### 19. `POST /api/pickup/{asNo}` — 예약 확정
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `asNo`
- **Request Body:**

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `pickupDate` | date | ✔ | |
| `slotStart` | time (`HH:mm:ss`) | ✔ | |
| `phone` | string | ✔ | 숫자만, 최대 50자 |
| `address` | string | ✔ | 최대 100자 |
| `addressDetail` | string | | 최대 100자 |
| `note` | string | | 최대 200자 |

- **응답 200:** `{ "pickupNo": "PKP-2026-000847" }`
- **주요 에러:** `VALIDATION_FAILED`, `PAST_DATE`, `SLOT_FULL`, `PICKUP_ALREADY_EXISTS`, `NO_AVAILABLE_DRIVER`, `INVALID_STATUS`, `NUMBER_CONFLICT`
  - 예약 확정 시 서버가 기사를 자동 배차한다(건별 수락 절차 없음).

### 20. `GET /api/pickup/complete/{pickupNo}` — 예약 완료 화면
### 21. `GET /api/pickup/{pickupNo}` — 예약 조회 (재진입)
- **권한:** MEMBER (본인 소유 건만) — 두 엔드포인트는 동일 응답
- **Path:** `pickupNo`
- **응답 200:**
```json
{
  "pickupNo": "PKP-2026-000847",
  "asNo": "AS-2026-00341",
  "pickupDate": "2026-08-20",
  "slotStart": "10:00:00",
  "slotEnd": "12:00:00",
  "address": "서울시 ...",
  "addressDetail": "101동 101호",
  "note": "부재 시 경비실",
  "phone": "01012345678",
  "status": "BOOKED",
  "driverName": "기사명",
  "driverAffiliation": "소속",
  "insuranceApplied": true,
  "insuranceLimit": 5000000
}
```

### 22. `DELETE /api/pickup/{pickupNo}` — 예약 취소
- **권한:** MEMBER (본인 소유 건만)
- **Path:** `pickupNo`
- **응답 200:** `{ "pickupNo": "PKP-2026-000847" }`
- **주요 에러:** `CANCEL_DEADLINE_PASSED`(예정일 24시간 전까지만 취소), `INVALID_STATUS`

---

## 기사 (Driver) — `/api/driver`

### 23. `POST /api/driver/login` — 기사 로그인
- **권한:** 공개
- **Request Body:** `{ "loginId": string(필수), "password": string(필수) }`
- **응답 200:**
```json
{ "accessToken": "eyJ...", "expiresIn": 7200, "driverId": 1, "name": "기사명" }
```
- **주요 에러:** `INVALID_CREDENTIALS`

### 24. `GET /api/driver/pickup/list` — 오늘의 수거 목록
- **권한:** DRIVER
- **응답 200:**
```json
{
  "pickupDate": "2026-08-18",
  "itemList": [
    {
      "pickupNo": "PKP-2026-000847",
      "slotStart": "10:00:00",
      "slotEnd": "12:00:00",
      "customerName": "홍길동",
      "phone": "01012345678",
      "address": "서울시 ...",
      "addressDetail": "101동 101호",
      "note": "부재 시 경비실",
      "modelName": "모델명",
      "damagePart": "손잡이",
      "asPhotoUrlList": ["https://.../files/...?..."]
    }
  ]
}
```
  - 인계에 필요한 정보를 모두 담으므로 별도 상세 조회 API가 없다.

### 25. `POST /api/driver/handover/{pickupNo}` — 인계 완료
- **권한:** DRIVER (오늘 본인에게 배정된 건만)
- **Path:** `pickupNo`
- **Content-Type:** `multipart/form-data`
- **Parts:**
  - `photos` (파일 배열): 1~5장(제품 상태 사진)
  - `customerSign` (파일): 고객 서명
  - `driverSign` (파일): 기사 서명
- **응답 200:**
```json
{ "pickupNo": "PKP-2026-000847", "handedOverAt": "2026-08-18T14:00:00" }
```
- **주요 에러:** `PHOTO_REQUIRED`, `TOO_MANY_HANDOVER_PHOTOS`, `NO_PERMISSION`, `ALREADY_HANDED_OVER`, `INVALID_PICKUP_DATE`, `INVALID_STATUS`

---

## 관리자 (Admin) — `/api/admin`

> `X-Admin-Key` 헤더로 보호된다(`AdminKeyFilter`). 화면은 없으며 Postman·curl로 호출한다.

### 26. `PUT /api/admin/asCase/{asNo}/status` — AS 상태 변경
- **권한:** `X-Admin-Key` 헤더 필수
- **Path:** `asNo`
- **Request Body:**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | AsStatus | ✔ | 변경할 상태 |
| `description` | string | | 이력 타임라인 문구 |
| `statusMessage` | string | | 상세 화면 "최신 상태 메시지" |
| `expectedCompletedAt` | date | | 예상 완료일 |
| `delayReason` | string | | 지연 사유 |
| `currentLocation` | string | | 현재 위치 |
| `locationType` | string | | 위치 유형 |
| `locationStatus` | string | | 위치 상태 |

- **응답 200:** `{ "asNo": "AS-2026-00341", "status": "REPAIRING" }`
- **전이 규칙:** 앞 단계로만 전이 가능(되돌리기·제자리 불가). `PICKED_UP`은 기사 인계로만 도달하며 관리자가 직접 전이 불가. 수거 전(ESTIMATED·PICKUP_BOOKED)에서는 전이 불가.
- **주요 에러:** `NO_PERMISSION`, `NO_MATCHING_DATA`, `INVALID_STATUS`

---

## AI 상담 챗봇 (Chat) — `/api/chat`

> 프론트 → 스프링 게이트웨이 → FastAPI 챗봇 서버 구조. 스프링은 챗봇 응답 JSON을 형태 변경 없이 그대로 통과시킨다. 챗봇 서버가 꺼져 있거나 실패하면 **503**과 안내 문구를 반환한다(화면이 깨지지 않도록). 요청 시 회원의 JWT(`Authorization`)를 챗봇으로 그대로 전달한다.

**서비스 불가 응답 (503):**
```json
{ "answer": "상담 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  "sources": [], "used_gpt": false, "masked": 0, "used_history": false }
```

### 27. `POST /api/chat` — 상담
- **권한:** MEMBER
- **Request Body:**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `message` | string | ✔ | 사용자 메시지 |
| `sessionId` | string | | 대화 맥락 유지용. 미지정 시 서버가 `m{회원ID}`로 고정 |
| `asNo` | string | | 접수 후 개인화 상담일 때만 채운다 |

- **응답 200:** 챗봇 서버의 JSON 응답을 그대로 전달(`application/json`).

### 28. `GET /api/chat/opening` — 상담 시작 인사말
- **권한:** MEMBER
- **Query:** `asNo` (선택) — 지정 시 해당 접수 내용 기반 인사말, 미지정 시 일반 인사말
- **응답 200:** 챗봇 서버의 JSON 응답을 그대로 전달.

### 29. `POST /api/chat/stream` — 상담 (SSE 스트리밍)
- **권한:** MEMBER
- **Request Body:** `POST /api/chat`와 동일 (`message`, `sessionId`, `asNo`)
- **응답 200:** `text/event-stream` (SSE). 챗봇 스트림을 버퍼링 없이 그대로 전달한다.
- **실패 시:** 스트림 본문에 `data: 상담 서버에 연결할 수 없습니다.\n\n` 전송.

---

## 파일 접근 — `/files/**`

- **권한:** 공개 경로이나 `SignedFileFilter`가 서명을 검증한다.
- 손상 사진·인계 사진·전자서명은 개인정보이므로 URL에 만료 시각과 서명 파라미터가 붙는다(`<img src>`로 표시 가능하도록 헤더 인증 대신 사용).
- 서명이 없거나 만료·불일치하면 **404 `NO_MATCHING_DATA`**(파일 존재 여부 미노출).
- 서명 URL은 각 API 응답의 `photoUrl`/`photoUrlList`/`thumbnailUrl`/`...SignUrl` 필드로 제공되며, 만료 시 화면 새로고침으로 새 URL을 받는다(기본 TTL 86400초).
