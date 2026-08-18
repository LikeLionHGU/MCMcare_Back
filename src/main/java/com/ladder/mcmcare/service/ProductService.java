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
     * 보증서 번호로 제품 정보를 조회한다. 715 접수 폼 자동 채움에 쓰인다.
     * 보증서가 없으면 404 이며, 프론트는 자동 채움만 생략하고 접수는 계속 진행한다.
     *
     * 소유자 검증을 하지 않는다.
     * 브랜드가 구매자 정보를 제공하지 않아 소유자를 특정할 방법이 없고,
     * 반환하는 값도 제품 종류 · 모델명 · 보증 정보뿐이라 개인정보가 포함되지 않는다.
     */
    public ProductDto.DetailResDto detail(String warrantyNo) {
        Product product = productRepository.findById(warrantyNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        return ProductDto.DetailResDto.from(product);
    }
}
