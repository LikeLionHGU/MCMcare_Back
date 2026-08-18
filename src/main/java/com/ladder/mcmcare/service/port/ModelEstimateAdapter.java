package com.ladder.mcmcare.service.port;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 진단 서버(FastAPI) 연동 어댑터.
 *
 * POST {ai-base-url}/diagnose/multi 로 사진 여러 장을 한 번에 보내고,
 * 카테고리별 병합 결과를 받아 화면 계약(EstimateResult)으로 변환한다.
 *
 * AI 서버는 금액을 EUR 로 반환하므로 EstimateMapper 가 원화로 환산한다.
 *
 * app.estimate.provider=model 일 때만 활성화된다.
 * 프로필 조합(prod,ai)에 의존하지 않으므로 어떤 환경에서든 설정 한 줄로 결정된다.
 *
 * ⚠️ 호출은 반드시 트랜잭션 밖에서 이루어져야 한다 (AsCaseFacade 가 보장).
 *    수 초~수십 초가 걸리는 외부 I/O 이므로 트랜잭션 안에 두면 커넥션 풀이 고갈된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.estimate.provider", havingValue = "model")
public class ModelEstimateAdapter implements EstimatePort {

    private final RestClient restClient;
    private final EstimateMapper mapper;
    private final Path uploadDir;
    private final String fileBaseUrl;

    public ModelEstimateAdapter(EstimateMapper mapper,
                                @Value("${app.estimate.ai-base-url}") String aiBaseUrl,
                                @Value("${app.estimate.ai-timeout-seconds}") int timeoutSeconds,
                                @Value("${app.file.upload-dir}") String uploadDir,
                                @Value("${app.file.base-url}") String fileBaseUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(factory)
                .build();
        this.mapper = mapper;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.fileBaseUrl = fileBaseUrl;
    }

    @Override
    public EstimateResult analyze(AsCase asCase, List<AsPhoto> photos) {

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (AsPhoto photo : photos) {
            Path file = resolveLocalPath(photo.getFileUrl());
            if (file == null || !Files.exists(file)) {
                log.warn("AI 전송 대상 파일을 찾을 수 없음: {}", photo.getFileUrl());
                continue;
            }
            body.add("files", new FileSystemResource(file));
        }

        if (body.isEmpty()) {
            throw new IllegalStateException("AI 서버로 보낼 사진 파일이 없습니다.");
        }

        AiDiagnoseResponse res = restClient.post()
                .uri("/diagnose/multi")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(AiDiagnoseResponse.class);

        if (res == null) {
            throw new IllegalStateException("AI 서버 응답이 비어 있습니다.");
        }

        return toEstimateResult(res, photos.size());
    }

    private EstimateResult toEstimateResult(AiDiagnoseResponse res, int photoCount) {

        List<AiDiagnoseResponse.Damage> damages =
                res.getDamages() == null ? List.of() : res.getDamages();

        // 손상 미탐지 — 비용 없이 안내 문구만 내려보낸다
        if (damages.isEmpty()) {
            return EstimateResult.builder()
                    .damageCategory("손상 미확인")
                    .damageSeverity(null)
                    .confidenceGrade("낮음")
                    .confidenceNote(mapper.confidenceNote(photoCount))
                    .items(List.of())
                    .noDamageNotice(EstimateMapper.NO_DAMAGE_NOTICE)
                    .build();
        }

        List<EstimateResult.Item> items = new ArrayList<>();
        List<String> categories = new ArrayList<>();

        for (AiDiagnoseResponse.Damage d : damages) {
            AiDiagnoseResponse.Cost cost = d.getEstimatedCostEur();
            if (cost == null) continue;

            categories.add(d.getMcmCategory());
            items.add(EstimateResult.Item.builder()
                    .repairItemName(d.getMcmCategory())
                    .estimatedPrice(mapper.toKrwRound(cost.getPointEstimate()))
                    .minPrice(mapper.toKrwFloor(cost.getMin()))
                    .maxPrice(mapper.toKrwCeil(cost.getMax()))
                    .build());
        }

        double maxConfidence = damages.stream()
                .map(AiDiagnoseResponse.Damage::getDetectionConfidence)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        return EstimateResult.builder()
                .damageCategory(mapper.joinCategories(categories))
                .damageSeverity(mapper.severityLabel(res.getOverallSeverity()))
                .confidenceGrade(mapper.confidenceGrade(maxConfidence))
                .confidenceNote(mapper.confidenceNote(photoCount))
                .items(items)
                .build();
    }

    /**
     * 저장된 공개 URL 을 로컬 파일 경로로 되돌린다.
     * as_photo 에는 URL 만 저장돼 있고, AI 서버에는 파일 본체를 보내야 하기 때문이다.
     *
     * S3 로 전환하면 이 메서드 대신 스트림을 내려받아 전송하도록 바꾸면 된다.
     */
    private Path resolveLocalPath(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(fileBaseUrl)) return null;
        String relative = fileUrl.substring(fileBaseUrl.length());
        if (relative.startsWith("/")) relative = relative.substring(1);

        Path resolved = uploadDir.resolve(relative).normalize();
        // 경로 조작으로 업로드 디렉터리 밖 파일이 읽히지 않도록 막는다
        return resolved.startsWith(uploadDir) ? resolved : null;
    }
}
