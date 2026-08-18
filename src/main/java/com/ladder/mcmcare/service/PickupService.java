package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsPhoto;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.Driver;
import com.ladder.mcmcare.domain.Member;
import com.ladder.mcmcare.domain.Pickup;
import com.ladder.mcmcare.domain.PickupSlot;
import com.ladder.mcmcare.domain.PickupStatus;
import com.ladder.mcmcare.dto.PickupDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.DriverRepository;
import com.ladder.mcmcare.repository.MemberRepository;
import com.ladder.mcmcare.repository.PickupRepository;
import com.ladder.mcmcare.repository.PickupSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PickupService {

    /** 취소 가능 마감 — 픽업 예정일 기준 */
    private static final int CANCEL_DEADLINE_HOURS = 24;

    private final PickupRepository pickupRepository;
    private final PickupSlotRepository slotRepository;
    private final DriverRepository driverRepository;
    private final MemberRepository memberRepository;
    private final AsCaseService asCaseService;
    private final AsStatusService asStatusService;
    private final NumberGenerator numberGenerator;
    private final FileUrlSigner fileUrlSigner;

    // ── Form (720 진입) ──────────────────────────────────────────

    public PickupDto.FormResDto form(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        String photoUrl = fileUrlSigner.sign(
                asCaseService.photosOf(asCase.getId()).stream()
                        .findFirst().map(AsPhoto::getFileUrl).orElse(null));

        String phone = memberRepository.findById(memberId)
                .map(Member::getPhone).orElse(null);

        return PickupDto.FormResDto.of(asCase, photoUrl, phone);
    }

    // ── Slot (720 예약 가능 슬롯) ────────────────────────────────

    public PickupDto.SlotResDto slots(PickupDto.SlotReqDto req) {

        LocalDate from = req.startOrToday();
        LocalDate to = req.endOrPlus14();

        List<PickupSlot> slots =
                slotRepository.findBySlotDateBetweenOrderBySlotDateAscSlotStartAsc(from, to);

        // 예약 건수를 (날짜, 시작시각) 으로 미리 집계한다
        Map<String, Long> booked = pickupRepository
                .findByPickupDateBetweenAndStatus(from, to, PickupStatus.BOOKED)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> key(p.getPickupDate(), p.getSlotStart()), Collectors.counting()));

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        Map<LocalDate, List<PickupDto.SlotItemDto>> grouped = new java.util.LinkedHashMap<>();

        for (PickupSlot s : slots) {
            boolean full = booked.getOrDefault(key(s.getSlotDate(), s.getSlotStart()), 0L) >= s.getCapacity();
            boolean past = s.getSlotDate().isEqual(today) && !s.getSlotStart().isAfter(now);

            grouped.computeIfAbsent(s.getSlotDate(), d -> new ArrayList<>())
                    .add(PickupDto.SlotItemDto.builder()
                            .slotStart(s.getSlotStart())
                            .slotEnd(s.getSlotEnd())
                            .available(!s.isBlocked() && !full && !past)
                            .build());
        }

        List<PickupDto.SlotDateDto> dates = grouped.entrySet().stream()
                .map(e -> PickupDto.SlotDateDto.builder()
                        .date(e.getKey()).slotList(e.getValue()).build())
                .toList();

        return PickupDto.SlotResDto.builder().dateList(dates).build();
    }

    private String key(LocalDate d, LocalTime t) {
        return d + "|" + t;
    }

    // ── Create (720 예약 확정) ───────────────────────────────────

    @Transactional
    public PickupDto.CreateResDto create(Long memberId, String asNo, PickupDto.CreateReqDto req) {

        // 락 순서: AS → 슬롯. 다른 경로와 순서가 엇갈리지 않도록 항상 이 순서를 지킨다.
        AsCase asCase = asCaseService.getOwnedForUpdate(memberId, asNo);

        if (asCase.getStatus() != AsStatus.ESTIMATED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        if (pickupRepository.existsByAsCaseIdAndStatus(asCase.getId(), PickupStatus.BOOKED)) {
            throw new BusinessException(ErrorCode.PICKUP_ALREADY_EXISTS);
        }

        // 슬롯 행을 잠근 뒤 정원을 확인한다
        PickupSlot slot = slotRepository
                .findForUpdate(req.getPickupDate(), req.getSlotStart())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));

        if (slot.isBlocked()) throw new BusinessException(ErrorCode.SLOT_FULL);

        LocalDate today = LocalDate.now();
        if (slot.getSlotDate().isBefore(today)
                || (slot.getSlotDate().isEqual(today) && !slot.getSlotStart().isAfter(LocalTime.now()))) {
            throw new BusinessException(ErrorCode.PAST_DATE);
        }

        long bookedCount = pickupRepository.countByPickupDateAndSlotStartAndStatus(
                slot.getSlotDate(), slot.getSlotStart(), PickupStatus.BOOKED);
        if (bookedCount >= slot.getCapacity()) {
            throw new BusinessException(ErrorCode.SLOT_FULL);
        }

        Driver driver = driverRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_AVAILABLE_DRIVER));

        Pickup pickup = Pickup.builder()
                .pickupNo(numberGenerator.generatePickupNo())
                .asCase(asCase)
                .driver(driver)
                .pickupDate(slot.getSlotDate())
                .slotStart(slot.getSlotStart())
                .slotEnd(slot.getSlotEnd())      // 요청값이 아닌 슬롯 마스터 값을 쓴다
                .phone(req.getPhone())
                .address(req.getAddress())
                .addressDetail(req.getAddressDetail())
                .note(req.getNote())
                .insuranceApplied(true)
                .insuranceLimit(5_000_000)
                .build();

        // 채번은 미커밋 동시 요청을 볼 수 없어 서로 다른 접수 건이 같은 순간에 예약하면
        // 같은 번호가 나올 수 있다.
        //
        // 여기서 재시도하지 않는 이유 — flush 가 실패하면 영속성 컨텍스트가 오염되어
        // 같은 트랜잭션에서 조회·저장을 이어갈 수 없다.
        // 같은 접수 건의 동시 예약은 위에서 잡은 AS 락이 이미 직렬화하므로
        // 남은 충돌은 드물고, 사용자가 다시 시도하면 해결된다.
        try {
            pickupRepository.saveAndFlush(pickup);
        } catch (DataIntegrityViolationException e) {
            log.warn("예약번호 충돌 pickupNo={}", pickup.getPickupNo(), e);
            throw new BusinessException(ErrorCode.NUMBER_CONFLICT);
        }

        asStatusService.transit(asCase, AsStatus.PICKUP_BOOKED, "픽업 예약 완료");

        return PickupDto.CreateResDto.from(pickup);
    }

    // ── Complete / Detail ────────────────────────────────────────

    public PickupDto.CompleteResDto detail(Long memberId, String pickupNo) {
        return PickupDto.CompleteResDto.from(getOwned(memberId, pickupNo));
    }

    // ── Cancel ───────────────────────────────────────────────────

    @Transactional
    public PickupDto.CancelResDto cancel(Long memberId, String pickupNo) {

        // 기사 인계도 같은 행을 잠그므로, 취소와 인계가 동시에 들어와도 한쪽만 성공한다.
        Pickup pickup = getOwnedForUpdate(memberId, pickupNo);

        if (pickup.getStatus() != PickupStatus.BOOKED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        LocalDateTime deadline = LocalDateTime.of(pickup.getPickupDate(), pickup.getSlotStart())
                .minusHours(CANCEL_DEADLINE_HOURS);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(ErrorCode.CANCEL_DEADLINE_PASSED);
        }

        pickup.cancel();
        asStatusService.transit(pickup.getAsCase(), AsStatus.ESTIMATED, "픽업 예약 취소");

        return PickupDto.CancelResDto.from(pickup);
    }

    // ── 내부 ─────────────────────────────────────────────────────

    public Pickup getOwned(Long memberId, String pickupNo) {
        Pickup p = pickupRepository.findByPickupNo(pickupNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        if (!p.getAsCase().isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return p;
    }

    /** 상태를 바꾸는 명령용 — 픽업 행을 잠근 뒤 반환한다. */
    private Pickup getOwnedForUpdate(Long memberId, String pickupNo) {
        Pickup p = pickupRepository.findByPickupNoForUpdate(pickupNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
        if (!p.getAsCase().isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return p;
    }
}
