package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.Product;
import com.ladder.mcmcare.dto.ProductDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 보증서가 없으면 404. 프론트는 자동 채움만 생략하고 접수는 계속 진행한다.
     *
     * 소유자 검증은 하지 않는다 — 보증서 번호만 알면 타인 구매정보가 노출되는 구조이나,
     * 기획에서 소유자 정책이 확정되지 않았다. product.member_id 컬럼은 준비돼 있다.
     */
    public ProductDto.DetailResDto detail(String warrantyNo) {
        Product product = productRepository.findById(warrantyNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        return ProductDto.DetailResDto.from(product);
    }
}
