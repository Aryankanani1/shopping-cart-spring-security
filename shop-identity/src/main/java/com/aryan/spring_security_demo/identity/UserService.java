package com.aryan.spring_security_demo.identity;
import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.common.exception.AuthenticationFailedException;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class UserService implements UserServiceInterface{
    /** BCrypt hashes only this many bytes; the encoder throws on longer input. */
    private static final int MAX_PASSWORD_BYTES = 72;
    /** Self-registration only ever creates customers. */

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtils authUtils;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher events;
    private final UserAccountWriter accountWriter;

    // getUserById and updateUser are called only from this class (the *Dto and
    // *AndConvert methods), so no @Transactional: it would never apply.
    private User getUserById(Long userId) {
        // Accounts are private: only the owner (or an admin) may read one.
        authUtils.requireSelfOrAdmin(userId);
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("failed to find user"));
    }

    private User updateUser(UserUpdateRequest request, Long userId) {
        authUtils.requireSelfOrAdmin(userId);
        return userRepository.findById(userId).map(existingUser -> {
            existingUser.setFirstName(request.getFirstName());
            existingUser.setLastName(request.getLastName());
          return userRepository.save(existingUser);
        }).orElseThrow(() -> new UserNotFoundException("failed to find user"));
    }


    @Override
    @Transactional
    public void deleteUser(Long userId) {
        authUtils.requireSelfOrAdmin(userId);
        userRepository.findById(userId).ifPresentOrElse(user -> {
            // refresh_tokens.user_id is a foreign key that User doesn't map (so no
            // cascade), and any signed-in user has at least one token row — clear
            // them first or the delete fails on the constraint (surfacing as 409).
            refreshTokenService.endAllSessions(userId);
            // Other modules settle their rows first: the cart is deleted
            // (CartIdentityListener), and orders still open are cancelled and
            // restocked (OrderIdentityListener). The rest of the orders are kept
            // for the shop's records: the database unlinks them.
            events.publishEvent(new UserDeletingEvent(userId));
            userRepository.delete(user);
        }, () -> {
            throw new UserNotFoundException("failed to find user");
        });
    }

    /**
     * Hash a new password, rejecting one too long for BCrypt as a 400 on
     * {@code field}. The request's 72-character limit isn't enough on its own: a
     * character can take up to 4 bytes, and the encoder's limit is in bytes.
     */
    private String encodePassword(String field, String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidPasswordException(field, "Password is too long");
        }
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public UserDto convertUserToDto(User user){
        return modelMapper.map(user,UserDto.class);
    }

    // ---- DTO-returning operations: load + map in ONE transaction --------------
    // Converting inside the transaction keeps any lazy association readable while
    // the session is open (open-in-view is off). Controllers call these and never
    // map a User entity themselves. The cart is not part of the account: it is
    // served by GET /carts/mine.

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserDtoById(Long userId) {
        return convertUserToDto(getUserById(userId));
    }

    /**
     * Sign up. Not {@code @Transactional}: the password is hashed first, with no
     * transaction (and so no pooled connection) held, since BCrypt is slow on
     * purpose; only the insert runs in a transaction, in {@link UserAccountWriter}.
     */
    @Override
    public UserDto createUserAndConvert(CreateUserRequest request) {
        // Checked before hashing, so a taken email is refused without the cost of
        // a hash; UserAccountWriter checks again in the transaction that inserts.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException(request.getEmail() + " already exists");
        }
        String passwordHash = encodePassword("password", request.getPassword());
        return accountWriter.createAccount(request, passwordHash);
    }

    @Override
    @Transactional
    public UserDto updateUserAndConvert(UserUpdateRequest request, Long userId) {
        return convertUserToDto(updateUser(request, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            // A request no authentication reached: answer 401, not a 500 from the NPE.
            throw new AuthenticationFailedException("Authentication required",
                    "No authentication in the security context");
        }
        String email = authentication.getName();
        return userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new UserNotFoundException("failed to find user"));
    }

    /**
     * Not {@code @Transactional}: both BCrypt steps (checking the current password,
     * hashing the new one) run with no transaction held. The current hash is read
     * on its own, and the update, with ending every session, is one short
     * transaction in {@link UserAccountWriter}.
     */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        // Always the caller's own account: an admin can't use this to set someone
        // else's password, since it hinges on knowing the current one.
        Long userId = authUtils.currentUserId();
        String currentHash = userRepository.findById(userId)
                .map(User::getPassword)
                .orElseThrow(() -> new UserNotFoundException("failed to find user"));

        // Re-verify even though the caller holds a valid token — a stolen or
        // left-open session alone must not be enough to take over the account.
        if (!passwordEncoder.matches(request.getCurrentPassword(), currentHash)) {
            throw new InvalidPasswordException("currentPassword", "Current password is incorrect");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new InvalidPasswordException("newPassword", "New password must be different from the current one");
        }
        String newHash = encodePassword("newPassword", request.getNewPassword());
        // Only replaces the hash if it is still the one checked above.
        accountWriter.replacePassword(userId, currentHash, newHash);
    }
}
