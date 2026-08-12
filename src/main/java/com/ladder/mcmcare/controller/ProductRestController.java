package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.dto.ProductDto;
import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import com.ladder.mcmcare.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;

    /** 715 보증서 자동 채움 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/{warrantyNo}")
    public ResponseEntity<ProductDto.DetailResDto> detail(
            @PathVariable String warrantyNo,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(productService.detail(principal.getId(), warrantyNo));
    }
}
