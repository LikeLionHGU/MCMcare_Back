package com.ladder.mcmcare.controller;

import com.ladder.mcmcare.dto.MemberDto;
import com.ladder.mcmcare.security.LoginMember;
import com.ladder.mcmcare.security.PrincipalDetails;
import com.ladder.mcmcare.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberRestController {

    private final MemberService memberService;

    /** 708 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<MemberDto.SignupResDto> signup(
            @Valid @RequestBody MemberDto.SignupReqDto reqDto) {
        return ResponseEntity.ok(memberService.signup(reqDto));
    }

    /** 707 로그인 */
    @PostMapping("/login")
    public ResponseEntity<MemberDto.LoginResDto> login(
            @Valid @RequestBody MemberDto.LoginReqDto reqDto) {
        return ResponseEntity.ok(memberService.login(reqDto));
    }

    /**
     * 707 구글 로그인.
     *
     * 프론트가 구글 SDK 로 받은 ID 토큰을 보내면 서버가 검증하고 우리 JWT 를 발급한다.
     * 리다이렉트를 쓰지 않으므로 응답 형태가 일반 로그인과 같다.
     */
    @PostMapping("/login/google")
    public ResponseEntity<MemberDto.GoogleLoginResDto> googleLogin(
            @Valid @RequestBody MemberDto.GoogleLoginReqDto reqDto) {
        return ResponseEntity.ok(memberService.googleLogin(reqDto));
    }

    /** 714 홈 — 최근 AS 5건 */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/home")
    public ResponseEntity<MemberDto.HomeResDto> home(
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(memberService.home(principal.getId()));
    }

    /** 마이페이지 (화면 미작성) */
    @PreAuthorize("hasRole('MEMBER')")
    @GetMapping("/info")
    public ResponseEntity<MemberDto.InfoResDto> info(
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(memberService.info(principal.getId()));
    }

    /** 정보 수정 · 마케팅 동의 철회 */
    @PreAuthorize("hasRole('MEMBER')")
    @PutMapping
    public ResponseEntity<MemberDto.UpdateResDto> update(
            @Valid @RequestBody MemberDto.UpdateReqDto reqDto,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(memberService.update(principal.getId(), reqDto));
    }

    /**
     * 회원 탈퇴 (소프트 삭제).
     *
     * 본인 확인은 Authorization 토큰과 모달의 "탈퇴" 문구 입력으로 대체한다.
     * 구글 계정에는 비밀번호가 없어 비밀번호 방식을 쓰지 않기로 했다 (프론트 설계).
     */
    @PreAuthorize("hasRole('MEMBER')")
    @DeleteMapping
    public ResponseEntity<MemberDto.MessageResDto> withdraw(
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(memberService.withdraw(principal.getId()));
    }

    /**
     * 비밀번호 변경 (로그인 상태에서만 가능).
     *
     * 구글 가입 회원은 변경 불가 (400 반환).
     * 이메일 찾기/재설정은 메일 인프라가 없으므로 구현하지 않는다.
     * PUT 을 쓰는 이유: 프론트(member.js)가 put() 으로 호출한다.
     */
    @PreAuthorize("hasRole('MEMBER')")
    @PutMapping("/password")
    public ResponseEntity<MemberDto.MessageResDto> changePassword(
            @Valid @RequestBody MemberDto.ChangePasswordReqDto reqDto,
            @LoginMember PrincipalDetails principal) {
        return ResponseEntity.ok(memberService.changePassword(principal.getId(), reqDto));
    }
}
