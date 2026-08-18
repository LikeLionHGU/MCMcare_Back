package com.ladder.mcmcare.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 업로드 파일 URL 에 만료 시각과 서명을 붙인다.
 *
 * 왜 필요한가
 *   손상 사진 · 인계 사진 · 전자서명은 개인정보에 해당한다.
 *   그런데 프론트가 &lt;img src="..."&gt; 로 표시하므로 Authorization 헤더를 실을 수 없다.
 *   그래서 URL 자체에 접근 권한을 담는다.
 *
 * 왜 DB 에는 서명 없는 URL 을 저장하는가
 *   서명에는 만료 시각이 들어간다. 저장해 두면 시간이 지나 무효가 된다.
 *   원본 URL 을 저장하고 응답을 만들 때마다 서명을 새로 붙이면
 *   사용자가 화면을 다시 열 때 항상 유효한 URL 을 받는다.
 *
 * 형식
 *   http://host/files/as/uuid.jpg?exp=1755400000&amp;sig=Base64UrlHmac
 */
@Slf4j
@Component
public class FileUrlSigner {

    public static final String PARAM_EXPIRES = "exp";
    public static final String PARAM_SIGNATURE = "sig";

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;
    private final long ttlSeconds;

    public FileUrlSigner(@Value("${app.file.sign-secret}") String secret,
                         @Value("${app.file.sign-ttl-seconds}") long ttlSeconds) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.ttlSeconds = ttlSeconds;
    }

    /** 원본 URL 에 만료 시각과 서명을 붙인다. null 이면 그대로 반환한다. */
    public String sign(String url) {
        if (url == null || url.isBlank()) return url;

        String path = pathOf(url);
        if (path == null) return url;   // 우리가 서빙하는 파일이 아니면 손대지 않는다

        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        return "%s?%s=%d&%s=%s".formatted(url, PARAM_EXPIRES, exp, PARAM_SIGNATURE, hmac(path, exp));
    }

    public List<String> sign(List<String> urls) {
        if (urls == null) return null;
        return urls.stream().map(this::sign).toList();
    }

    /**
     * 요청 경로와 쿼리 파라미터가 유효한지 검증한다.
     *
     * @param path 컨텍스트 패스를 제외한 요청 경로 (예: /files/as/uuid.jpg)
     */
    public boolean verify(String path, String expires, String signature) {
        if (expires == null || signature == null) return false;

        long exp;
        try {
            exp = Long.parseLong(expires);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Instant.now().getEpochSecond() > exp) return false;

        // 서명 비교는 상수 시간으로 한다
        return MessageDigest.isEqual(
                hmac(path, exp).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** 절대 URL 에서 /files/... 부분만 뽑는다. 서명 대상이 아니면 null. */
    private String pathOf(String url) {
        int idx = url.indexOf("/files/");
        return idx < 0 ? null : url.substring(idx);
    }

    private String hmac(String path, long exp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] raw = mac.doFinal((path + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("파일 URL 서명에 실패했습니다.", e);
        }
    }
}
