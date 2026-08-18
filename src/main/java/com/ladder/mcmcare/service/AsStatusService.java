package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.AsStatusHistory;
import com.ladder.mcmcare.repository.AsStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태 전이 단일 진입점.
 *
 * as_case.status 와 as_status_history 를 반드시 같은 트랜잭션에서 갱신한다.
 * 이 메서드를 거치지 않고 status 를 직접 바꾸면
 * 목록 화면의 뱃지와 상세 화면의 타임라인이 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class AsStatusService {

    private final AsStatusHistoryRepository historyRepository;

    @Transactional
    public void transit(AsCase asCase, AsStatus next, String description) {
        transit(asCase, next, description, null);
    }

    @Transactional
    public void transit(AsCase asCase, AsStatus next, String description, String statusMessage) {
        asCase.changeStatus(next, statusMessage);
        historyRepository.save(AsStatusHistory.of(asCase, next, description));
    }
}
