package com.ladder.mcmcare.dto;

import com.ladder.mcmcare.domain.Product;
import lombok.*;

import java.time.LocalDate;

public class ProductDto {

    /**
     * 715 제품 정보 입력 — 보증서 번호 자동 채움.
     *
     * 구매처는 반환하지 않는다. 브랜드 보증 조회로는 구매처가 확인되지 않으므로
     * 사용자가 직접 선택한다. (스토리보드와 상충하는 지점 — 기획 확인 필요)
     */
    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DetailResDto {
        private String warrantyNo;
        private String productType;
        private String productTypeLabel;
        private String modelName;
        private LocalDate purchasedAt;

        /**
         * 구매처. 715 화면에서 보증서 번호를 넣으면 이 값도 함께 채워진다.
         * 접수 요청(POST /api/asCase)에 purchaseChannel 을 실어야 하므로 code 도 함께 준다.
         */
        private String purchaseChannel;
        private String purchaseChannelLabel;
        private int warrantyMonths;
        private LocalDate warrantyExpiresAt;
        private String warrantyScope;

        public static DetailResDto from(Product p) {
            return DetailResDto.builder()
                    .warrantyNo(p.getWarrantyNo())
                    .productType(p.getProductType().name())
                    .productTypeLabel(p.getProductType().getLabel())
                    .modelName(p.getModelName())
                    .purchasedAt(p.getPurchasedAt())
                    .purchaseChannel(p.getPurchaseChannel() == null
                            ? null : p.getPurchaseChannel().name())
                    .purchaseChannelLabel(p.getPurchaseChannel() == null
                            ? null : p.getPurchaseChannel().getLabel())
                    .warrantyMonths(p.getWarrantyMonths())
                    .warrantyExpiresAt(p.getWarrantyExpiresAt())
                    .warrantyScope(p.getWarrantyScope())
                    .build();
        }
    }
}
