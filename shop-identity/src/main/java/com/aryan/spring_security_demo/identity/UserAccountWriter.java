package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The database writes of signing up and changing a password, each in a short
 * transaction of its own. {@link UserService} hashes the password before calling
 * these: BCrypt is slow on purpose, and a transaction holds a pooled connection
 * until it ends, so hashing inside one kept a connection busy for the whole hash.
 *
 * <p>A bean of its own because the calls must come in through Spring's proxy for
 * {@code @Transactional} to apply; a call from one method of UserService to another
 * would skip it.
 */
@Service
@RequiredArgsConstructor
class UserAccountWriter {

    private static final String CUSTOMER_ROLE = "ROLE_CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final ModelMapper modelMapper;

    /** Save a new customer account with an already-hashed password. */
    @Transactional
    public UserDto createAccount(CreateUserRequest request, String passwordHash) {
        // UserService checked too, before hashing; this is the check that holds,
        // in the transaction that inserts the row.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AlreadyExistsException(request.getEmail() + " already exists");
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPassword(passwordHash);
        user.getRoles().add(customerRole());
        return modelMapper.map(userRepository.save(user), UserDto.class);
    }

    /**
     * Replace the password hash and end every session, in one transaction, so the
     * change never commits while a stolen refresh token stays usable. Only if the
     * stored hash is still {@code verifiedHash}, the one the current password was
     * checked against: if the password changed in between, that check no longer
     * holds and nothing is changed.
     */
    @Transactional
    public void replacePassword(Long userId, String verifiedHash, String newHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("failed to find user"));
        // Constant-time, as any comparison of password hashes should be.
        if (!MessageDigest.isEqual(user.getPassword().getBytes(StandardCharsets.UTF_8),
                verifiedHash.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidPasswordException("currentPassword", "Current password is incorrect");
        }
        user.setPassword(newHash);
        refreshTokenService.endAllSessions(userId);
    }

    /** DataInitializer creates the role at startup, so it is always there. */
    private Role customerRole() {
        return roleRepository.findByName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(CUSTOMER_ROLE + " is missing"));
    }
}
