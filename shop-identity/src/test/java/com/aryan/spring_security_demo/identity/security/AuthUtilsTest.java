package com.aryan.spring_security_demo.identity.security;

import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The ownership check behind every cart, order and account endpoint (IDOR protection). */
class AuthUtilsTest {

    private final AuthUtils authUtils = new AuthUtils();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_isTheSignedInUsersId() {
        signIn(7L, "ROLE_CUSTOMER");

        assertThat(authUtils.currentUserId()).isEqualTo(7L);
    }

    @Test
    void owner_mayActOnTheirOwnResource() {
        signIn(7L, "ROLE_CUSTOMER");

        assertThatCode(() -> authUtils.requireSelfOrAdmin(7L)).doesNotThrowAnyException();
    }

    @Test
    void customer_mayNotActOnSomeoneElsesResource() {
        signIn(7L, "ROLE_CUSTOMER");

        assertThatThrownBy(() -> authUtils.requireSelfOrAdmin(8L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void admin_mayActOnAnyonesResource() {
        signIn(1L, "ROLE_ADMIN");

        assertThatCode(() -> authUtils.requireSelfOrAdmin(8L)).doesNotThrowAnyException();
        assertThat(authUtils.isAdmin()).isTrue();
    }

    @Test
    void resourceWithNoOwner_isDeniedEvenToAnAdmin() {
        signIn(1L, "ROLE_ADMIN");

        assertThatThrownBy(() -> authUtils.requireSelfOrAdmin(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noAuthentication_isDeniedNotANullPointer() {
        assertThatThrownBy(authUtils::currentUserId).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authUtils.requireSelfOrAdmin(7L)).isInstanceOf(AccessDeniedException.class);
        assertThat(authUtils.isAdmin()).isFalse();
    }

    private static void signIn(Long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        ShopUserDetails principal = new ShopUserDetails(id, "user" + id + "@example.com", "hash", authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
