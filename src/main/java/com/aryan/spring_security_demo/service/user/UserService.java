package com.aryan.spring_security_demo.service.user;
import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.exception.UserNotFoundException;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.UserRepository;
import com.aryan.spring_security_demo.request.CreateUserRequest;
import com.aryan.spring_security_demo.request.UserUpdateRequest;
import com.aryan.spring_security_demo.security.AuthUtils;
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
        userRepository.findById(userId).ifPresentOrElse(userRepository::delete, () -> {
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
}
