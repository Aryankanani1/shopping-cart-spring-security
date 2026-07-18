package com.aryan.spring_security_demo.Service.user;

import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.request.CreateUserRequest;
import com.aryan.spring_security_demo.request.UserUpdateRequest;

import java.util.Optional;

public interface UserServiceInterface {
    User getUserById(Long userId);

    User createUser(CreateUserRequest request);
    User updateUser(UserUpdateRequest request, Long userId);
    void deleteUser(Long userId);

    UserDto convertUserToDto(User user);

    User getAuthenticatedUser();
}
