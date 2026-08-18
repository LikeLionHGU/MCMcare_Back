package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 상담 챗봇(FastAPI) 게이트웨이.
 *
 * 8/17 회의에서 정한 구조 — 프론트는 스프링 한 곳만 부르고,
 * 스프링이 파이썬 서버(챗봇·AI 진단)를 대신 호출한다.
 * 프론트가 챗봇 주소·CORS·인증을 따로 처리하지 않아도 되고,
 * Swagger 문서도 스프링 한 곳으로 모인다.
 *
 * 챗봇 서버가 죽어도 이 컨트롤러는 예외를 던지지 않고 안내 문구를 돌려준다.
 * 시연 중 상담이 안 된다고 화면 전체가 깨지면 안 되기 때문이다.
 *
 *  챗봇 호출은 GPT 응답을 기다리므로 수 초가 걸린다.
 *    트랜잭션 안에서 부르지 않는다 (여기서는 서비스 계층을 거치지 않으므로 안전).
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatRestController {

    private final RestClient restClient;
    private final boolean enabled;

    public ChatRestController(@Value("${app.chatbot.base-url:}") String chatbotUrl,
                              @Value("${app.chatbot.timeout-seconds:60}") int timeoutSeconds) {

        this.enabled = chatbotUrl != null && !chatbotUrl.isBlank();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(this.enabled ? chatbotUrl : "http://localhost:8001")
                .requestFactory(factory)
                .build();
    }

    /** 프론트 → 스프링 요청 본문. asNo 는 접수 후 상담(717)일 때만 채운다. */
    public record ChatReqDto(String message, String sessionId, String asNo) {}

    /**
     * 상담 (717 개인화 · 718 이력 없음 공용).
     *
     * 챗봇 응답 JSON 을 그대로 흘려보낸다. 스프링이 형태를 바꾸면
     * 챗봇 쪽 필드가 늘어날 때마다 여기도 같이 고쳐야 하므로 문자열로 통과시킨다.
     */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping
    public ResponseEntity<String> chat(@RequestBody ChatReqDto req,
                                       @LoginMember PrincipalDetails principal,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION,
                                                      required = false) String auth) {
        if (!enabled) {
            return unavailable();
        }
        try {
            String body = restClient.post()
                    .uri("/chat")
                    .headers(h -> forward(h, auth))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(req, principal))
                    .retrieve()
                    .body(String.class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

        } catch (Exception e) {
            log.warn("[챗봇] 호출 실패 — {}", e.getMessage());
            return unavailable();
        }
    }

    /**
     * 상담 시작 인사말.
     *
     * asNo 를 주면 "AS-2026-00871 건이 현재 '수선 중' 단계입니다" 처럼
     * 접수 내용으로 인사한다. 없으면 일반 인사말이 나온다.
     */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/opening")
    public ResponseEntity<String> opening(@RequestParam(required = false) String asNo,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION,
                                                         required = false) String auth) {
        if (!enabled) {
            return unavailable();
        }
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/chat/opening")
                            .queryParamIfPresent("as_id",
                                    java.util.Optional.ofNullable(asNo))
                            .build())
                    .headers(h -> forward(h, auth))
                    .retrieve()
                    .body(String.class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

        } catch (Exception e) {
            log.warn("[챗봇] 인사말 호출 실패 — {}", e.getMessage());
            return unavailable();
        }
    }

    /**
     * 스트리밍 상담 (타자 치듯 한 글자씩 나오는 효과).
     *
     * SSE 라서 응답을 통째로 받아 넘기면 효과가 사라진다.
     * 스트림을 버퍼링 없이 그대로 복사해 내려보낸다.
     *
     * 프론트가 스트리밍을 안 쓰면 이 메서드는 지워도 된다. /api/chat 만으로 동작한다.
     */
    @PreAuthorize("hasRole('MEMBER')")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody ChatReqDto req,
                                                        @LoginMember PrincipalDetails principal,
                                                        @RequestHeader(value = HttpHeaders.AUTHORIZATION,
                                                                       required = false) String auth) {

        StreamingResponseBody body = out -> {
            try {
                restClient.post()
                        .uri("/chat/stream")
                        .headers(h -> forward(h, auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload(req, principal))
                        .exchange((request, response) -> {
                            response.getBody().transferTo(out);
                            out.flush();
                            return null;
                        });
            } catch (Exception e) {
                log.warn("[챗봇] 스트리밍 실패 — {}", e.getMessage());
                out.write("data: 상담 서버에 연결할 수 없습니다.\n\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
            }
        };

        return ResponseEntity.ok()
                // nginx 뒤에 두면 이 헤더가 없을 때 응답이 버퍼링돼 스트리밍이 안 보인다
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(body);
    }

    /**
     * 챗봇이 받는 형태로 바꾼다. 챗봇은 snake_case 를 쓴다.
     *
     * sessionId 가 없으면 로그인 회원 기준으로 고정한다.
     * 프론트가 매 요청마다 새 값을 만들면 대화 맥락이 끊긴다
     * ("치수 알려줘" → "5건입니다" → "12CO001이요" 가 이어지지 않는다).
     */
    private Map<String, Object> payload(ChatReqDto req, PrincipalDetails principal) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", req.message());
        body.put("session_id",
                req.sessionId() != null && !req.sessionId().isBlank()
                        ? req.sessionId()
                        : "m" + principal.getId());
        if (req.asNo() != null && !req.asNo().isBlank()) {
            body.put("as_id", req.asNo());
        }
        return body;
    }

    /**
     * 고객의 JWT 를 챗봇으로 그대로 넘긴다.
     *
     * 챗봇은 답변을 만들면서 우리 API(/api/asCase/detail, /api/asCase/estimate)를 다시 부른다.
     * 그 API 들은 토큰으로 소유자를 검증하므로(getOwned), 토큰이 없으면 401 이 되고
     * 챗봇은 조용히 더미 데이터(as_dummy.json)로 답한다 — 에러가 나지 않아 알아채기 어렵다.
     *
     * 고정 토큰(SPRING_API_TOKEN)으로 대체하면 안 된다.
     * 모든 고객이 그 토큰 주인으로 취급되어 자기 접수 건도 404 가 된다.
     */
    private void forward(HttpHeaders headers, String auth) {
        if (auth != null && !auth.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, auth);
        }
    }

    /** 챗봇이 꺼져 있거나 죽었을 때. 화면이 깨지지 않도록 정상 형태로 돌려준다. */
    private ResponseEntity<String> unavailable() {
        return ResponseEntity.status(503)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                      {"answer":"상담 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                       "sources":[],"used_gpt":false,"masked":0,"used_history":false}
                      """);
    }
}
