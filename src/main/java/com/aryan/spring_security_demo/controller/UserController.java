package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.user.UserServiceInterface;
import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
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

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserServiceInterface userServiceInterface;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getUserById(@PathVariable Long id){
        try{
            User user = userServiceInterface.getUserById(id);
            UserDto userDto = userServiceInterface.convertUserToDto(user);
            return ResponseEntity.ok(new ApiResponse("success!",userDto));
        }catch (ResourceNotFoundException e){
return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse> createUser(@Valid @RequestBody CreateUserRequest createUserRequest){
        try {
            User user = userServiceInterface.createUser(createUserRequest);
                UserDto userDto = userServiceInterface.convertUserToDto(user);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(userDto.getId()).toUri();
            return ResponseEntity.created(location).body(new ApiResponse("success!",userDto));
        } catch (AlreadyExistsException e) {
            return  ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse> updateUser(@Valid @RequestBody UserUpdateRequest request,@PathVariable Long userId){
        try {
            User user = userServiceInterface.updateUser(request, userId);
            UserDto userDto = userServiceInterface.convertUserToDto(user);
            return ResponseEntity.ok(new ApiResponse("success!",userDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long userId){
        try {
             userServiceInterface.deleteUser(userId);
            return ResponseEntity.noContent().build();
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }
}
