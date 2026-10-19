package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import com.aryan.spring_security_demo.identity.security.user.ShopUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified access token into the caller, for the rest of the request.
 *
 * <p>The user is loaded again from the database rather than taken from the
 * token's claims, so a deleted account or a removed role takes effect on the
 * next request, not when the token expires. The principal is then the same
 * {@link ShopUserDetails} a login produces, which AuthUtils and
 * {@code @AuthenticationPrincipal} read.
 *
 * <p>A failure here is an {@code AuthenticationException}, which the resource
 * server answers with a 401 (JwtEntryPoint).
 *
 * <p>Made by ShopConfig rather than a {@code @Component}: Spring MVC registers
 * every {@code Converter} bean as a type converter of its own, and
 * {@code @WebMvcTest} slices pick them up, without the user service it needs.
 */
@RequiredArgsConstructor
public class ShopJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ShopUserDetailsService userDetailsService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // An unknown email throws UsernameNotFoundException: the account is gone.
        ShopUserDetails user = userDetailsService.loadUserByUsername(jwt.getSubject());
        // The token was made for one account. If that account's email now belongs
        // to another (the first deleted, the email signed up again), it isn't this one's.
        Object id = jwt.getClaim(JwtUtils.ID_CLAIM);
        if (!(id instanceof Number number) || !Long.valueOf(number.longValue()).equals(user.getId())) {
            throw new BadCredentialsException("The token was made for another account");
        }
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
