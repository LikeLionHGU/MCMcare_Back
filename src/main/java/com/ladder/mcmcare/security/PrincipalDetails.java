package com.ladder.mcmcare.security;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 고객(member)과 기사(driver)가 같은 인증 체계를 공유한다.
 * id 는 각 테이블의 PK 이며, role 로 어느 테이블인지 구분한다.
 */
@Getter
@Builder
public class PrincipalDetails implements UserDetails {

    private final Long id;
    private final String username;   // member.email 또는 driver.login_id
    private final String password;
    private final Role role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isMember() {
        return role == Role.MEMBER;
    }

    public boolean isDriver() {
        return role == Role.DRIVER;
    }
}
