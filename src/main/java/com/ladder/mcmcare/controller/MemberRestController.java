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
}
