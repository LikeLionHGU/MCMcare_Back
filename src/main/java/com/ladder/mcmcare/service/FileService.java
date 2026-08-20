package com.ladder.mcmcare.service;

import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 파일 저장. 현재는 로컬 디스크에 저장하고 /files/** 로 서빙한다.
 * S3 로 전환하려면 이 클래스만 교체하면 된다.
 */
@Slf4j
@Service
public class FileService {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");

    private final Path uploadDir;
    private final String baseUrl;

    public FileService(@Value("${app.file.upload-dir}") String uploadDir,
                       @Value("${app.file.base-url}") String baseUrl) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath();
        this.baseUrl = baseUrl;
    }

    public String upload(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path target = uploadDir.resolve(subDir).resolve(filename);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", target, e);
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return "%s/%s/%s".formatted(baseUrl, subDir, filename);
    }

    /**
     * 여러 파일을 저장한다. 중간에 실패하면 앞서 저장한 것까지 지운다.
     *
     * 정리하지 않으면 3장 중 2번째가 실패했을 때 1번째가 디스크에 남는다.
     * 호출부는 예외를 받고 DB 저장을 하지 않으므로, 그 파일을 참조할 방법이 영영 없다.
     */
    public List<String> upload(List<MultipartFile> files, String subDir) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_REQUIRED);
        }

        List<String> saved = new ArrayList<>();
        try {
            for (MultipartFile f : files) {
                saved.add(upload(f, subDir));
            }
        } catch (RuntimeException e) {
            deleteQuietly(saved);
            throw e;
        }
        return saved;
    }

    /**
     * 저장했던 파일을 지운다. DB 처리가 실패해 파일만 남는 것을 막는 용도다.
     *
     * 삭제 자체가 실패해도 예외를 던지지 않는다 —
     * 이미 다른 예외를 처리하는 중이고, 정리 실패로 원래 원인을 덮으면 안 된다.
     */
    public void deleteQuietly(List<String> urls) {
        if (urls == null) return;
        for (String url : urls) {
            deleteQuietly(url);
        }
    }

    public void deleteQuietly(String url) {
        Path target = resolveLocalPath(url);
        if (target == null) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("업로드 파일 정리 실패: {}", target, e);
        }
    }

    /**
     * 공개 URL 을 로컬 경로로 되돌린다.
     * 업로드 디렉터리 밖을 가리키면 null 을 반환해 임의 경로 삭제를 막는다.
     */
    private Path resolveLocalPath(String url) {
        if (url == null || !url.startsWith(baseUrl)) return null;

        String relative = url.substring(baseUrl.length());
        int q = relative.indexOf('?');                 // 서명 파라미터 제거
        if (q >= 0) relative = relative.substring(0, q);
        if (relative.startsWith("/")) relative = relative.substring(1);
        if (relative.isBlank()) return null;

        Path resolved = uploadDir.resolve(relative).normalize();
        return resolved.startsWith(uploadDir) ? resolved : null;
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
