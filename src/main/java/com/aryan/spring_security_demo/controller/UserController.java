package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.user.UserServiceInterface;
import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.request.CreateUserRequest;
import com.aryan.spring_security_demo.request.UserUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserServiceInterface userServiceInterface;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getUserById(@PathVariable Long id){
        User user = userServiceInterface.getUserById(id);
        UserDto userDto = userServiceInterface.convertUserToDto(user);
        return ResponseEntity.ok(new ApiResponse<>("success!", userDto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createUser(@Valid @RequestBody CreateUserRequest createUserRequest){
        User user = userServiceInterface.createUser(createUserRequest);
        UserDto userDto = userServiceInterface.convertUserToDto(user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(userDto.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>("success!", userDto));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> updateUser(@Valid @RequestBody UserUpdateRequest request, @PathVariable Long userId){
        User user = userServiceInterface.updateUser(request, userId);
        UserDto userDto = userServiceInterface.convertUserToDto(user);
        return ResponseEntity.ok(new ApiResponse<>("success!", userDto));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> deleteUser(@PathVariable Long userId){
        userServiceInterface.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
