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

    public List<String> upload(List<MultipartFile> files, String subDir) {
        return files.stream().map(f -> upload(f, subDir)).toList();
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
