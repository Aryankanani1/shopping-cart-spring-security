package com.aryan.spring_security_demo.identity;



public interface UserServiceInterface {
    User getUserById(Long userId);

    User createUser(CreateUserRequest request);
    User updateUser(UserUpdateRequest request, Long userId);
    void deleteUser(Long userId);

    UserDto convertUserToDto(User user);

    // Load + convert inside one transaction so the DTO's lazy graph
    // (cart -> cartItems, orders -> orderItems) resolves before the session closes.
    UserDto getUserDtoById(Long userId);
    UserDto createUserAndConvert(CreateUserRequest request);
    UserDto updateUserAndConvert(UserUpdateRequest request, Long userId);

    User getAuthenticatedUser();

    /**
     * Change the authenticated caller's password after re-verifying the current
     * one, and end every existing session in the same transaction.
     */
    void changePassword(ChangePasswordRequest request);
}
