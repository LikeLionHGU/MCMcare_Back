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
     * 소유자가 지정된 보증서는 본인만 조회할 수 있다.
     * 지정되지 않은 보증서(member_id 가 null)는 아직 등록 전이므로 조회를 허용한다 —
     * 최초 접수 시 자동 채움이 동작해야 하기 때문이다.
     *
     * ⚠️ 소유자 등록 시점 정책은 기획 확정 대기 중이다.
     */
    public ProductDto.DetailResDto detail(Long memberId, String warrantyNo) {

        Product product = productRepository.findById(warrantyNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (product.getMember() != null
                && !product.getMember().getId().equals(memberId)) {
            // 존재 여부 자체를 숨긴다 — 403 이면 번호가 유효하다는 사실이 드러난다
            throw new BusinessException(ErrorCode.NO_MATCHING_DATA);
        }

        return ProductDto.DetailResDto.from(product);
    }
}
