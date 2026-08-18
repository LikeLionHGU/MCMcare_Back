package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.dto.AdminDto;
import com.ladder.mcmcare.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * X-Admin-Key 헤더로 보호된다 (AdminKeyFilter).
 * 화면은 없으며 Postman · curl 로 호출한다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRestController {

    private final AdminService adminService;

    @PutMapping("/asCase/{asNo}/status")
    public ResponseEntity<AdminDto.UpdateStatusResDto> updateStatus(
            @PathVariable String asNo,
            @Valid @RequestBody AdminDto.UpdateStatusReqDto reqDto) {
        return ResponseEntity.ok(adminService.updateStatus(asNo, reqDto));
    }
}
