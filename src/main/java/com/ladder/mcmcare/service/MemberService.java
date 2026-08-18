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
    private final GoogleTokenVerifier googleTokenVerifier;

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

        // 소셜 가입 회원은 비밀번호가 없다.
        // 그대로 matches() 에 넘기면 IllegalArgumentException 으로 500 이 나간다.
        // 계정 존재 여부를 드러내지 않도록 비밀번호 오류와 같은 응답을 준다.
        if (member.getPassword() == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(req.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtProvider.createToken(member.getId(), member.getEmail(), Role.MEMBER);
        return MemberDto.LoginResDto.of(member, token, jwtProvider.getExpiresInSeconds());
    }

    // ── Google Login ─────────────────────────────────────────────

    /**
     * 구글 계정으로 로그인한다. 계정이 없으면 만든다.
     *
     * 같은 이메일로 이미 가입한 회원이 있으면 그 계정에 연결한다.
     * 별도 계정을 만들면 같은 사람의 AS 내역이 두 계정으로 갈린다.
     *
     * 구글은 이메일 소유를 확인해 주므로(email_verified), 이메일 일치를
     * 본인 확인 근거로 삼아도 된다.
     */
    @Transactional
    public MemberDto.GoogleLoginResDto googleLogin(MemberDto.GoogleLoginReqDto req) {

        GoogleTokenVerifier.GoogleUser google = googleTokenVerifier.verify(req.getIdToken());

        Member member = memberRepository.findByEmail(google.getEmail()).orElse(null);
        boolean newMember = (member == null);

        if (newMember) {
            member = memberRepository.save(
                    Member.ofGoogle(google.getEmail(), google.getName()));

            // 마케팅 동의는 받은 적이 없으므로 미동의로 기록한다
            marketingConsentRepository.save(MarketingConsent.of(member, false));
        }

        String token = jwtProvider.createToken(member.getId(), member.getEmail(), Role.MEMBER);
        return MemberDto.GoogleLoginResDto.of(
                member, token, jwtProvider.getExpiresInSeconds(), newMember);
    }

    // ── Home ─────────────────────────────────────────────────────

    public MemberDto.HomeResDto home(Long memberId) {
        // 취소 건과 중간 상태(DRAFT · ANALYZING)는 제외한다
        List<AsCase> cases = asCaseRepository
                .findByMemberIdAndStatusInOrderByCreatedAtDesc(
                        memberId, AsStatus.visibleValues(),
                        PageRequest.of(0, HOME_AS_CASE_LIMIT))
                .getContent();
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
