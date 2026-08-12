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
import org.springframework.beans.factory.annotation.Value;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PickupService {

    /** 취소 가능 마감 — 픽업 예정일 기준 */
    private static final int CANCEL_DEADLINE_HOURS = 24;

    /** 예약번호 UNIQUE 충돌 시 재시도 횟수 */
    private static final int MAX_NO_RETRY = 3;

    private final PickupRepository pickupRepository;
    private final PickupSlotRepository slotRepository;
    private final DriverRepository driverRepository;
    private final MemberRepository memberRepository;
    private final AsCaseService asCaseService;
    private final AsStatusService asStatusService;
    private final NumberGenerator numberGenerator;

    @Value("${app.demo.auto-handover}")
    private boolean autoHandover;

    // ── Form (720 진입) ──────────────────────────────────────────

    public PickupDto.FormResDto form(Long memberId, String asNo) {

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

        String photoUrl = asCaseService.photosOf(asCase.getId()).stream()
                .findFirst().map(AsPhoto::getFileUrl).orElse(null);

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

        AsCase asCase = asCaseService.getOwned(memberId, asNo);

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

        // 예약번호 UNIQUE 충돌 시 재발급 (채번은 미커밋 동시 요청을 볼 수 없다)
        for (int attempt = 0; ; attempt++) {
            try {
                pickupRepository.saveAndFlush(pickup);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_NO_RETRY) throw e;
                pickup.reassignPickupNo(numberGenerator.generatePickupNo());
            }
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

        Pickup pickup = getOwned(memberId, pickupNo);

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

    public boolean isAutoHandoverEnabled() {
        return autoHandover;
    }
}
