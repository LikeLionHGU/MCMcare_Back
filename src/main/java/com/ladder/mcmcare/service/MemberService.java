package com.ladder.mcmcare.service;

import com.ladder.mcmcare.domain.AsCase;
import com.ladder.mcmcare.domain.AsStatus;
import com.ladder.mcmcare.domain.MarketingConsent;
import com.ladder.mcmcare.domain.Member;
import com.ladder.mcmcare.dto.MemberDto;
import com.ladder.mcmcare.exception.BusinessException;
import com.ladder.mcmcare.exception.ErrorCode;
import com.ladder.mcmcare.repository.AsCaseRepository;
import com.ladder.mcmcare.repository.MarketingConsentRepository;
import com.ladder.mcmcare.repository.MemberRepository;
import com.ladder.mcmcare.security.JwtProvider;
import com.ladder.mcmcare.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    /** 홈 화면에 노출되는 AS 건 수 */
    private static final int HOME_AS_CASE_LIMIT = 5;

    private final MemberRepository memberRepository;
    private final MarketingConsentRepository marketingConsentRepository;
    private final AsCaseRepository asCaseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // ── Signup ───────────────────────────────────────────────────

    @Transactional
    public MemberDto.SignupResDto signup(MemberDto.SignupReqDto req) {

        if (!req.getPassword().equals(req.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (req.getBirthDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.FUTURE_BIRTH_DATE);
        }
        if (!req.isAgreedService() || !req.isAgreedPrivacy()) {
            throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED);
        }
        if (memberRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        Member member = memberRepository.save(
                req.toEntity(passwordEncoder.encode(req.getPassword())));

        // 마케팅 동의는 시점 기록이 법적 의무이므로 이력으로 남긴다
        marketingConsentRepository.save(
                MarketingConsent.of(member, req.isAgreedMarketing()));

        return MemberDto.SignupResDto.from(member);
    }

    // ── Login ────────────────────────────────────────────────────

    public MemberDto.LoginResDto login(MemberDto.LoginReqDto req) {

        Member member = memberRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(req.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.createToken(member.getId(), member.getEmail(), Role.MEMBER);
        return MemberDto.LoginResDto.of(member, token, jwtProvider.getExpiresInSeconds());
    }

    // ── Home ─────────────────────────────────────────────────────

    public MemberDto.HomeResDto home(Long memberId) {
        List<AsCase> cases = asCaseRepository
                .findByMemberIdAndStatusNotOrderByCreatedAtDesc(
                        memberId, AsStatus.CANCELLED, PageRequest.of(0, HOME_AS_CASE_LIMIT));
        return MemberDto.HomeResDto.from(cases);
    }

    // ── Info ─────────────────────────────────────────────────────

    public MemberDto.InfoResDto info(Long memberId) {
        Member member = getMember(memberId);
        return MemberDto.InfoResDto.of(member, currentMarketingConsent(member));
    }

    // ── Update ───────────────────────────────────────────────────

    @Transactional
    public MemberDto.UpdateResDto update(Long memberId, MemberDto.UpdateReqDto req) {

        Member member = getMember(memberId);
        member.updateProfile(req.getName(), req.getPhone());

        // 동의 값이 바뀐 경우에만 이력을 추가한다
        if (currentMarketingConsent(member) != req.isAgreedMarketing()) {
            marketingConsentRepository.save(
                    MarketingConsent.of(member, req.isAgreedMarketing()));
        }

        return MemberDto.UpdateResDto.from(member);
    }

    // ── 내부 ─────────────────────────────────────────────────────

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_MATCHING_DATA));
    }

    private boolean currentMarketingConsent(Member member) {
        return marketingConsentRepository
                .findFirstByMemberOrderByOccurredAtDesc(member)
                .map(MarketingConsent::isAgreed)
                .orElse(false);
    }
}
