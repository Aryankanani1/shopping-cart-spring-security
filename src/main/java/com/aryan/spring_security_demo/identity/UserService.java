package com.aryan.spring_security_demo.identity;
import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserServiceInterface{
    /** BCrypt hashes only this many bytes; the encoder throws on longer input. */
    private static final int MAX_PASSWORD_BYTES = 72;
    /** Self-registration only ever creates customers. */
    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtils authUtils;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher events;
    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        // Accounts are private: only the owner (or an admin) may read one.
        authUtils.requireSelfOrAdmin(userId);
        return userRepository.findByIdWithCart(userId).orElseThrow(() -> new UserNotFoundException("failed to find user"));
    }

    @Override
    @Transactional
    public User createUser(CreateUserRequest request) {
        return Optional.of(request).filter(user -> !userRepository.existsByEmail(request.getEmail()))
                .map(req -> {
                    User user = new User();
                    user.setEmail(request.getEmail());
                    user.setFirstName(request.getFirstName());
                    user.setLastName(request.getLastName());
                    user.setPassword(encodePassword("password", request.getPassword()));
                    user.getRoles().add(customerRole());
                    return userRepository.save(user);
                }).orElseThrow(() -> new AlreadyExistsException( request.getEmail()+ " already exists"));
    }

    @Override
    @Transactional
    public User updateUser(UserUpdateRequest request, Long userId) {
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
            // Orders still open are cancelled and restocked first (OrderIdentityListener).
            // The rest are kept for the shop's records: the database unlinks them.
            events.publishEvent(new UserDeletingEvent(userId));
            userRepository.delete(user);
        }, () -> {
            throw new UserNotFoundException("failed to find user");
        });
    }

    /** DataInitializer creates the role at startup, so it is always there. */
    private Role customerRole() {
        return roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(CUSTOMER_ROLE + " is missing"));
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
    // UserDto pulls in the cart (-> cartItems -> products), all lazy.
    // findByIdWithCart fetches the cart, but its nested cartItems still
    // lazy-load. Converting
    // inside the transaction lets those resolve while the session is open —
    // without this, ModelMapper walks a lazy collection after the tx closed and
    // (open-in-view=false) throws LazyInitializationException. Controllers call
    // these and never map a User entity themselves.

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserDtoById(Long userId) {
        return convertUserToDto(getUserById(userId));
    }

    @Override
    @Transactional
    public UserDto createUserAndConvert(CreateUserRequest request) {
        return convertUserToDto(createUser(request));
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
        String email = authentication.getName();
        return userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new UserNotFoundException("failed to find user"));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // Always the caller's own account: an admin can't use this to set someone
        // else's password, since it hinges on knowing the current one.
        Long userId = authUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("failed to find user"));

        // Re-verify even though the caller holds a valid token — a stolen or
        // left-open session alone must not be enough to take over the account.
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("currentPassword", "Current password is incorrect");
        }
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new InvalidPasswordException("newPassword", "New password must be different from the current one");
        }
        user.setPassword(encodePassword("newPassword", request.getNewPassword()));

        // Same transaction as the password update, so the change can never commit
        // while a stolen refresh token stays usable.
        refreshTokenService.endAllSessions(userId);
    }
}
