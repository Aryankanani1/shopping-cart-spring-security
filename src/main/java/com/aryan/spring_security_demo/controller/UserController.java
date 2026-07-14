package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.user.UserServiceInterface;
import com.aryan.spring_security_demo.dto.UserDto;
import com.aryan.spring_security_demo.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.request.CreateUserRequest;
import com.aryan.spring_security_demo.request.UserUpdateRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("${api.prefix}/users")
@RequiredArgsConstructor
public class UserController {

    private final UserServiceInterface userServiceInterface;

    @GetMapping("/{id}/user")
    public ResponseEntity<ApiResponse> getUserById(@PathVariable Long id){
        try{
            User user = userServiceInterface.getUserById(id);
            UserDto userDto = userServiceInterface.convertUserToDto(user);
            return ResponseEntity.ok(new ApiResponse("success!",userDto));
        }catch (ResourceNotFoundException e){
return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse> createUser(@RequestBody CreateUserRequest createUserRequest){
        try {
            User user = userServiceInterface.createUser(createUserRequest);
                UserDto userDto = userServiceInterface.convertUserToDto(user);
            return ResponseEntity.ok(new ApiResponse("success!",userDto));
        } catch (AlreadyExistsException e) {
            return  ResponseEntity.status(CONFLICT).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @PutMapping("/{userId}/update")
    public ResponseEntity<ApiResponse> updateUser(@RequestBody UserUpdateRequest request,@PathVariable Long userId){
        try {
            User user = userServiceInterface.updateUser(request, userId);
            UserDto userDto = userServiceInterface.convertUserToDto(user);
            return ResponseEntity.ok(new ApiResponse("success!",userDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }

    @DeleteMapping("/{userId}/delete")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long userId){
        try {
             userServiceInterface.deleteUser(userId);
            return ResponseEntity.ok(new ApiResponse("success!",null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(),null));
        }
    }
}

