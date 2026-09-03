package com.aryan.spring_security_demo.security;

import com.aryan.spring_security_demo.security.user.UserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Object-level authorization helper. Reads the authenticated principal straight
 * from the {@link SecurityContextHolder} — the id and roles are already carried
 * in the JWT-derived {@link UserDetails}, so no database round-trip is needed —
 * and enforces that a caller may only act on the resources it owns, unless it is
 * an admin.
 *
 * <p>Centralising the rule means every endpoint throws the same
 * {@link AccessDeniedException} (rendered as {@code 403} by the global handler)
 * instead of re-implementing an ownership check — and getting it subtly wrong —
 * per controller. It closes the IDOR gap where a user could read or mutate
 * another user's cart, order or account simply by guessing its id.
 */
@Component
public class AuthUtils {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** The id of the currently authenticated user. */
    public Long currentUserId() {
        return principal().getId();
    }

    /** Whether the current caller holds the admin role. */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    /**
     * Assert the caller is {@code ownerId} (or an admin), throwing
     * {@link AccessDeniedException} otherwise. A {@code null} owner is treated as
     * "not yours" so a resource with no owner can never be reached by a
     * non-admin.
     */
    public void requireSelfOrAdmin(Long ownerId) {
        if (ownerId == null || (!isAdmin() && !ownerId.equals(currentUserId()))) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }

    private UserDetails principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            throw new AccessDeniedException("Authentication required");
        }
        return userDetails;
    }
}
