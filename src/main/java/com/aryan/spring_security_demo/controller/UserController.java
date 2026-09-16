package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.service.user.UserServiceInterface;
import com.aryan.spring_security_demo.dto.UserDto;
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
        UserDto userDto = userServiceInterface.getUserDtoById(id);
        return ResponseEntity.ok(new ApiResponse<>("success!", userDto));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createUser(@Valid @RequestBody CreateUserRequest createUserRequest){
        UserDto userDto = userServiceInterface.createUserAndConvert(createUserRequest);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(userDto.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>("success!", userDto));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> updateUser(@Valid @RequestBody UserUpdateRequest request, @PathVariable Long userId){
        UserDto userDto = userServiceInterface.updateUserAndConvert(request, userId);
        return ResponseEntity.ok(new ApiResponse<>("success!", userDto));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> deleteUser(@PathVariable Long userId){
        userServiceInterface.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
