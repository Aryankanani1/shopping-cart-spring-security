package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import com.aryan.spring_security_demo.identity.security.user.ShopUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A verified token becomes the account it was made for, as the database has it now. */
class ShopJwtAuthenticationConverterTest {

    private final ShopUserDetailsService users = mock(ShopUserDetailsService.class);
    private final ShopJwtAuthenticationConverter converter = new ShopJwtAuthenticationConverter(users);

    @Test
    void theCaller_isTheUserAsLoadedNow_withTheirCurrentRoles() {
        // The token still says customer; the database says admin since.
        ShopUserDetails ada = new ShopUserDetails(7L, "ada@example.com", "hash",
                List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(users.loadUserByUsername("ada@example.com")).thenReturn(ada);

        AbstractAuthenticationToken caller = converter.convert(token(7L, "ROLE_CUSTOMER"));

        assertThat(caller.isAuthenticated()).isTrue();
        assertThat(caller.getPrincipal()).isSameAs(ada);
        assertThat(caller.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void aDeletedAccount_isRefused() {
        when(users.loadUserByUsername("ada@example.com")).thenThrow(new UsernameNotFoundException("User not found"));

        assertThatThrownBy(() -> converter.convert(token(7L, "ROLE_CUSTOMER")))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // The email now belongs to an account that signed up after the first was deleted.
    @Test
    void aTokenForAnotherAccountWithTheSameEmail_isRefused() {
        when(users.loadUserByUsername("ada@example.com"))
                .thenReturn(new ShopUserDetails(12L, "ada@example.com", "hash", List.of()));

        assertThatThrownBy(() -> converter.convert(token(7L, "ROLE_CUSTOMER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void aTokenWithoutAnId_isRefused() {
        when(users.loadUserByUsername("ada@example.com"))
                .thenReturn(new ShopUserDetails(7L, "ada@example.com", "hash", List.of()));
        Jwt noId = Jwt.withTokenValue("token").header("alg", "RS256").subject("ada@example.com").build();

        assertThatThrownBy(() -> converter.convert(noId)).isInstanceOf(BadCredentialsException.class);
    }

    private static Jwt token(long id, String role) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("ada@example.com")
                .claim(JwtUtils.ID_CLAIM, id).claim(JwtUtils.ROLES_CLAIM, List.of(role)).build();
    }
}
