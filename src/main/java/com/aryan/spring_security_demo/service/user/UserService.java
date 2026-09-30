package com.aryan.spring_security_demo.service.user;
import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.exception.InvalidPasswordException;
import com.aryan.spring_security_demo.exception.UserNotFoundException;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.request.ChangePasswordRequest;
import com.aryan.spring_security_demo.request.CreateUserRequest;
import com.aryan.spring_security_demo.request.UserUpdateRequest;
import com.aryan.spring_security_demo.security.AuthUtils;
import com.aryan.spring_security_demo.service.auth.RefreshTokenService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserServiceInterface{
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthUtils authUtils;
    private final RefreshTokenService refreshTokenService;
    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        // Accounts are private: only the owner (or an admin) may read one.
        authUtils.requireSelfOrAdmin(userId);
        return userRepository.findByIdWithDetails(userId).orElseThrow(() -> new UserNotFoundException("failed to find user"));
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
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
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
            userRepository.delete(user);
        }, () -> {
            throw new UserNotFoundException("failed to find user");
        });
    }

    @Override
    public UserDto convertUserToDto(User user){
        return modelMapper.map(user,UserDto.class);
    }

    // ---- DTO-returning operations: load + map in ONE transaction --------------
    // UserDto pulls in cart (-> cartItems) and orders (-> orderItems), all lazy.
    // findByIdWithDetails can only JOIN FETCH orders + cart (two bags is the
    // limit), so the nested cartItems/orderItems still lazy-load. Converting
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
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Same transaction as the password update, so the change can never commit
        // while a stolen refresh token stays usable.
        refreshTokenService.endAllSessions(userId);
    }
}
