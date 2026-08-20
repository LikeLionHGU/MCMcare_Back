<div align="center">

# CUSTODIA — Backend

**MCM 제품 AS 수선을 위한 AI 견적·픽업·진행 추적 서비스**

2026 LIKELION UNIV. 14th Hackathon · ANIMAL LEAGUE · SJF Track
<br />

</div>

<br />

## 서비스 소개

**CUSTODIA**(라틴어 *custodia*: 보관·수호)는 MCM 제품이 손상됐을 때
**사진 한 장으로 수선 가능 여부와 비용 범위를 미리 확인**하고,
집에서 픽업 수거로 접수한 뒤 **수선 전 과정을 단계별로 추적**하는 AS 서비스입니다.

---

## 기획 배경

### 기존의 문제

명품 가방이 손상되면 고객은 이런 상황에 놓입니다.

1. **비용을 모른 채 매장에 가야 한다** — 수선비가 10만 원인지 50만 원인지 알 수 없어, 맡길지 말지 판단할 근거가 없음
2. **수선이 가능한지조차 불확실하다** — 비세토스 코팅 박리처럼 원단 교체가 필요한 손상은 "수선 불가" 판정이 나는 경우가 많음
3. **맡긴 뒤 깜깜이가 된다** — 2~3주 걸리는 수선 기간 동안 지금 어디서 무엇이 진행 중인지 알 수 없음

### 우리의 접근

**방문 전 판단 근거를 먼저 준다.**

손상 사진을 올리면 AI가 유형을 분류하고 비용 범위를 산출합니다.
"고칠 수 있는가 · 얼마쯤 드는가"를 집에서 확인한 뒤 접수를 결정할 수 있습니다.

접수 후에는 **픽업완료 → 진단 → 수선 → 검수 → 발송 → 완료** 9단계를
날짜와 함께 보여주고, AI 컨시어지가 그 접수 건의 실제 데이터를 읽어 답변합니다.

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **AI 손상 진단** | YOLOv8s 모델이 사진에서 손상 유형·위치·면적비를 판정 |
| **수선 비용 추정** | 손상 심각도에 따라 추정 금액과 범위를 산출. 근거 수준(확인됨/부분확인/가설)도 함께 제공 |
| **보증 적용 판정** | 보증서 번호로 구매일·보증 기간을 조회해 무상 수선 가능성 안내 |
| **픽업 수거 예약** | 30분 단위 슬롯 예약, 기사 배정, 전자서명 기반 인계 |
| **진행 상황 추적** | 9단계 상태와 이력을 타임라인으로 표시 (리페어 패스포트) |
| **AI 상담** | 접수 건의 실제 데이터를 읽어 진행 상황·수선 내용을 답변 |

---

## 시스템 구성

세 개의 서비스가 함께 동작합니다. 이 저장소는 그중 **백엔드**입니다.

```
                          프론트엔드 (React)
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │   백엔드 (Spring Boot)  │  ← 이 저장소
                    │        :8081           │
                    └───┬────────────────┬───┘
                        │                │
              ┌─────────▼──────┐  ┌──────▼─────────┐
              │  AI 진단 :8000  │  │ AI 상담 :8001  │
              │  YOLOv8s       │  │ GPT            │
              └────────────────┘  └───────┬────────┘
                                          │
                                    백엔드 API 재호출
                                    (고객 토큰 전달)
```

**AI 상담은 백엔드를 다시 호출합니다.** 고객의 JWT 를 그대로 전달해
본인 접수 건만 읽도록 하고, 상담 세션은 토큰 해시로 격리합니다.

| 저장소 | 내용 |
|---|---|
| [CUSTODIA_Front](https://github.com/LikeLionHGU/CUSTODIA_Front) | 프론트엔드 (React · Vite) |
| **MCMcare_Back** | **백엔드 (Spring Boot) — 이 저장소** |
| [MCMcare_Back_AI](https://github.com/LikeLionHGU/MCMcare_Back_AI) | AI 진단 · AI 상담 (FastAPI) |

---

## 기술 스택

<img src="https://img.shields.io/badge/Java_21-007396?style=for-the-badge&logo=openjdk&logoColor=white" /> <img src="https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" /> <img src="https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" /> <img src="https://img.shields.io/badge/JPA_Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white" />

<img src="https://img.shields.io/badge/MySQL_8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white" /> <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" /> <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" /> <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=github-actions&logoColor=white" />

| 구분 | 사용 기술 |
|---|---|
| 언어 · 프레임워크 | Java 21 · Spring Boot 4.1.0 |
| 데이터 | Spring Data JPA · Hibernate · MySQL 8.0 |
| 인증 | Spring Security · JWT (jjwt 0.12.6) · Google OAuth2 (ID Token) |
| 문서 | SpringDoc OpenAPI 3.1 (Swagger UI) |
| 배포 | Docker Compose · GitHub Actions (self-hosted runner) · Cloudflare Tunnel |

---

## 설계에서 신경 쓴 것

### AI 호출은 트랜잭션 밖에서

AI 분석은 수십 초가 걸립니다. 트랜잭션 안에서 호출하면 그동안 DB 커넥션을 붙들게 됩니다.

```
① TX  접수 저장 (DRAFT)
② TX  ANALYZING 으로 상태 선점       ← 락
③     AI 호출                        ← TX 밖
④ TX  결과 반영 (ESTIMATED)          ← 락 + 상태 재확인
```

`ANALYZING` 으로 선점해 두면 분석 중에는 취소가 막힙니다.
선점하지 않으면 "취소했는데 나중에 도착한 AI 결과가 접수를 되살리는" 일이 생깁니다.

### 견적은 저장하고 다시 부르지 않는다

전에는 견적 화면을 열 때마다 AI 를 재호출했습니다.
같은 접수 건인데 열어볼 때마다 금액이 달라지고, 조회에 수십 초가 걸렸습니다.

접수 시점에 `estimate` · `estimate_item` 에 저장하고, 조회는 DB 를 읽습니다.

### 같은 자원을 다투는 경로는 같은 락을 쓴다

픽업 하나를 두고 **고객 취소 · 기사 수동 인계 · 자동 인계 스케줄러**가 동시에 움직입니다.
한 경로만 락을 빠뜨리면 나머지 경로의 상태 검사가 무의미해집니다.

```java
// 세 경로가 모두 같은 행을 잠근다
getOwnedForUpdate(memberId, pickupNo)      // 고객 취소
findByPickupNoForUpdate(pickupNo)          // 기사 인계
findByIdForUpdate(pickupId)                // 자동 인계
```

락 획득 순서도 맞췄습니다 (`AS → SLOT`). 역전되면 데드락이 납니다.

### 파일은 URL 만으로 열리지 않는다

손상 사진 · 전자서명은 개인정보입니다. HMAC-SHA256 서명과 만료 시각을 붙여
URL 을 알아도 서명 없이는 접근할 수 없게 했습니다.

```
/files/as/uuid.jpg?exp=1787340655&sig=oGws1KX3flhCPFFJ...
```

경로를 서명에 포함해 다른 파일로 재사용할 수 없습니다.

### 신뢰도를 부풀리지 않는다

AI 가 손상을 0.91 확신해도, 가방 전체가 사진에 안 잡혔다면
손상 면적비를 이미지 전체 기준으로 계산한 것이라 심각도가 부정확합니다.

이 경우 신뢰도 등급을 한 단계 낮추고 이유를 함께 표시합니다.

```
분석 신뢰도  보통
             제출 사진 2장 기반 · 가방 전체가 사진에 담기지 않아
             손상 범위 추정이 부정확할 수 있습니다
```

---

## API

**31개 엔드포인트 · 전부 구현 완료.** 전체 명세는 별도 문서를 참고하세요.

- Swagger UI: `{서버주소}/swagger-ui.html`

### 그룹 요약

| 그룹 | 개수 | 내용 |
|---|---:|---|
| `/api/member` | 8 | 회원가입 · 로그인 · 구글 로그인 · 정보 수정 · 탈퇴 · 비밀번호 변경 |
| `/api/asCase` | 8 | 접수 · AI 견적 · 목록 · 상세 · 인계 확인 · 취소 |
| `/api/pickup` | 6 | 슬롯 조회 · 예약 · 변경 · 취소 · 완료 확인 |
| `/api/chat` | 3 | AI 상담 게이트웨이 (인사말 · 대화 · 스트리밍) |
| `/api/driver` | 3 | 기사 로그인 · 수거 목록 · 인계 완료 |
| `/api/product` | 1 | 보증서 조회 (제품 정보 자동 채움) |
| `/api/admin` | 1 | 수선 상태 전이 |
| `/api/health` | 1 | 배포 유지 확인 |

### 인증

세 가지 방식을 씁니다.

| 방식 | 대상 | 전달 |
|---|---|---|
| JWT | 회원 · 기사 | `Authorization: Bearer {accessToken}` |
| Admin Key | 관리자 API | `X-Admin-Key: {key}` |
| 서명 URL | 파일 | 쿼리의 `exp` + `sig` |

<details>
<summary><b>주요 API 상세 보기</b></summary>

### `POST /api/asCase` — AS 접수

`multipart/form-data`. 사진 1~4장과 JSON 을 함께 보냅니다.
접수 저장 → AI 분석 → 견적 저장까지 한 번에 처리합니다.

**Request**

```
request:  application/json
images:   file × 1~4
```

```json
{
  "warrantyNo": "20091123",
  "productType": "LUGGAGE",
  "modelName": "MCM 스타크 비세토스 캐리어 미디엄",
  "purchasedAt": "2025-12-20",
  "purchaseChannel": "OFFICIAL_STORE",
  "damagePart": "측면 하단",
  "damageType": "ETC",
  "damageDescription": "모서리가 찢어져 내용물이 보입니다",
  "photoTypeList": ["PRODUCT", "DAMAGE"]
}
```

**Response**

```json
{ "asNo": "AS-2026-00110" }
```

AI 분석에 실패하면 `502 ESTIMATE_FAILED` 와 함께 `asNo` 를 돌려줍니다.
접수는 저장돼 있으므로 재분석 API 로 다시 시도할 수 있습니다.

---

### `GET /api/asCase/estimate/{asNo}` — AI 예상 견적

접수 시점에 저장한 결과를 읽습니다. **AI 를 다시 호출하지 않습니다.**

```json
{
  "asNo": "AS-2026-00110",
  "damageCategory": "찢김/파열 · 균열/파손",
  "damageSeverity": "중간 — 부분 수선 가능 수준",
  "confidenceGrade": "보통",
  "confidenceNote": "제출 사진 2장 기반 · 가방 전체가 사진에 담기지 않아 손상 범위 추정이 부정확할 수 있습니다",
  "itemList": [
    {
      "repairItemName": "찢김/파열",
      "estimatedPrice": 199000,
      "minPrice": 145000,
      "maxPrice": 319000,
      "costConfidence": "가설"
    }
  ],
  "totalEstimatedPrice": 199000,
  "warrantyVerdict": "PARTIAL",
  "warrantyVerdictLabel": "부분 보증 적용 가능성 있음",
  "warrantyNoteList": [
    "해당 손상이 제조 결함으로 확인되면 무상 수선이 적용될 수 있습니다.",
    "정상 사용에 따른 소모로 분류될 경우 보증 적용이 제한될 수 있습니다."
  ]
}
```

`estimatedPrice` 는 손상 정도가 반영된 추정 금액입니다.
`minPrice`~`maxPrice` 만 쓰면 경미한 손상과 심각한 손상이 같은 범위로 표시됩니다.

---

### `GET /api/asCase/detail/{asNo}` — 접수 상세

```json
{
  "asNo": "AS-2026-00106",
  "modelName": "MCM 소프트쉘 캐리어 미디엄",
  "photoUrlList": ["https://.../files/demo/as/demo-5.jpg?exp=...&sig=..."],
  "damagePart": "측면 원단",
  "damageTypeLabel": "기타",
  "damageCategory": "찢김/파열",
  "status": "REPAIRING",
  "statusLabel": "수선중",
  "expectedCompletedAt": "2026-08-26",
  "currentLocation": "MCM 서울 수선 센터",
  "historyList": [
    { "statusLabel": "픽업완료", "completed": true,  "occurredAt": "2026-08-07" },
    { "statusLabel": "수선중",   "completed": true,  "occurredAt": "2026-08-12" },
    { "statusLabel": "검수중",   "completed": false }
  ]
}
```

손상 정보는 두 종류입니다. `damageTypeLabel` 은 **고객이 접수 때 고른 것**,
`damageCategory` 는 **AI 가 사진을 보고 판정한 것**입니다. 둘이 다를 수 있습니다.

`historyList` 의 `completed` 가 `false` 면 아직 오지 않은 단계입니다.

---

### `POST /api/chat` — AI 상담

백엔드는 게이트웨이 역할만 합니다. 고객의 JWT 를 챗봇에 전달해
**본인 접수 건만** 읽도록 합니다.

```json
{
  "message": "제 가방 지금 어디까지 진행됐나요?",
  "sessionId": "m1",
  "asNo": "AS-2026-00106"
}
```

```json
{
  "answer": "이서연 고객님, 현재 고객님의 MCM 소프트쉘 캐리어 미디엄은 수선 중에 있습니다...",
  "sources": ["내 AS 접수 AS-2026-00106", "진행 상황 확인"],
  "used_gpt": true,
  "session_id": "m1"
}
```

챗봇 서버가 꺼져 있으면 `503` 과 함께 같은 형태의 안내 문구를 돌려줍니다.
화면이 깨지지 않습니다.

</details>

---

## 데이터 모델

13개 테이블로 구성됩니다.

| 도메인 | 테이블 |
|---|---|
| 회원 | `member` · `marketing_consent` |
| 제품 | `product` (보증서) |
| 접수 | `as_case` · `as_photo` · `as_status_history` |
| 견적 | `estimate` · `estimate_item` |
| 픽업 | `pickup` · `pickup_slot` · `driver` |
| 인계 | `handover` · `handover_photo` |

### 상태 전이

```
DRAFT → ANALYZING → ESTIMATED → PICKUP_BOOKED → PICKED_UP
      → RECEIVED → DIAGNOSED → REPAIRING → INSPECTING → SHIPPING → COMPLETED
```

- `PICKED_UP` 진입은 **기사·자동 인계 전용**입니다
- 관리자는 **바로 다음 단계로만** 옮길 수 있습니다. 건너뛰면 타임라인이 모순됩니다
- 취소는 `ESTIMATED` · `ESTIMATE_FAILED` 에서만 가능합니다

---

## 실행 방법

### 요구 사항

- Java 21
- Docker · Docker Compose
- MySQL 8.0 (Docker 로 함께 실행)

### 환경 변수

`.env` 파일을 만들고 아래 값을 채웁니다.
운영 환경에서는 앞의 네 값이 기본값 그대로면 기동이 중단됩니다.

```bash
# 필수 — openssl rand -hex 32 로 생성
JWT_SECRET=
ADMIN_KEY=
FILE_SIGN_SECRET=
DB_PASSWORD=

# 데이터베이스
DB_ROOT_PASSWORD=
DB_NAME=mcmcare

# 파일
FILE_BASE_URL=http://localhost:8080/files
UPLOAD_DIR=./uploads

# 인증
GOOGLE_CLIENT_ID=

# AI 서버 — stub 이면 규칙 기반 견적 (AI 서버 불필요)
ESTIMATE_PROVIDER=stub
AI_BASE_URL=http://localhost:8000
CHATBOT_BASE_URL=http://localhost:8001

# CORS
CORS_ORIGINS=http://localhost:5173,http://localhost:3000
```

### 실행

```bash
docker compose up -d --build
```

기동 후 확인합니다.

```bash
curl http://localhost:8081/api/health
# {"status":"UP"}
```

### 시연 데이터

```bash
docker compose exec -T db mysql -uroot -p"$DB_PASSWORD" mcmcare \
  < src/main/resources/seed.sql
```

상태 9종을 하나씩 가진 접수 8건과 테스트 계정이 들어갑니다.
**몇 번을 실행해도 결과가 같습니다.** 시연 데이터만 갈아끼우고 사용자 데이터는 건드리지 않습니다.

| 계정 | 아이디 | 비밀번호 |
|---|---|---|
| 회원 | `user@example.com` | `Password123!` |
| 기사 | `driver01` | `Driver123!` |

---

## 배포

`main` 브랜치에 머지하면 GitHub Actions 가 자동 배포합니다.

```
main 푸시 → self-hosted runner → 빌드 → 컨테이너 재시작 → 헬스체크
```

`seed.sql` 이 변경된 경우에만 시드를 다시 주입합니다.
매번 실행하면 코드 한 줄 고칠 때마다 시연 데이터가 초기화되기 때문입니다.

Actions 탭에서 `Run workflow` 로 수동 배포도 가능하며,
이때는 시연 데이터 초기화 여부를 선택할 수 있습니다.
