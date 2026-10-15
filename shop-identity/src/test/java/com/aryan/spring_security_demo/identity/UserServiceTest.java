package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import com.aryan.spring_security_demo.common.exception.AuthenticationFailedException;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account rules: registration, password change and deletion. The repository,
 * encoder, ownership check and session service are mocked.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthUtils authUtils;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private ApplicationEventPublisher events;
    @Mock private UserAccountWriter accountWriter;

    @InjectMocks private UserService userService;

    // ---- registration -----------------------------------------------------

    // The hash is made before UserAccountWriter opens the transaction that saves
    // the account: hashing inside it held a pooled connection for the whole hash.
    @Test
    void createUserAndConvert_hashesFirst_thenSavesTheHashNotThePassword() {
        CreateUserRequest request = registration("secret123");
        UserDto saved = new UserDto();
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(accountWriter.createAccount(request, "hashed")).thenReturn(saved);

        assertThat(userService.createUserAndConvert(request)).isSameAs(saved);

        InOrder order = inOrder(passwordEncoder, accountWriter);
        order.verify(passwordEncoder).encode("secret123");
        order.verify(accountWriter).createAccount(request, "hashed");
    }

    @Test
    void createUserAndConvert_takenEmail_isRejectedWithoutHashing() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUserAndConvert(registration("secret123")))
                .isInstanceOf(AlreadyExistsException.class);
        verify(passwordEncoder, never()).encode(any());
        verify(accountWriter, never()).createAccount(any(), any());
    }

    @Test
    void createUserAndConvert_passwordOver72Bytes_isAFieldErrorNotAnEncoderCrash() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.createUserAndConvert(registration("é".repeat(40))))  // 80 bytes
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("password"));
        verify(passwordEncoder, never()).encode(any());
        verify(accountWriter, never()).createAccount(any(), any());
    }

    // ---- password change --------------------------------------------------

    @Test
    void changePassword_wrongCurrentPassword_changesNothing() {
        User user = signedInUserWithHash("old-hash");
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(passwordChange("wrong", "n3w-secret")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("currentPassword"));
        assertThat(user.getPassword()).isEqualTo("old-hash");
        verify(accountWriter, never()).replacePassword(any(), any(), any());
    }

    @Test
    void changePassword_unchangedPassword_isRejected() {
        signedInUserWithHash("old-hash");
        when(passwordEncoder.matches("same-pass", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(passwordChange("same-pass", "same-pass")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("newPassword"));
        verify(accountWriter, never()).replacePassword(any(), any(), any());
    }

    @Test
    void changePassword_newPasswordOver72Bytes_isRejected() {
        signedInUserWithHash("old-hash");
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(passwordChange("old-pass", "é".repeat(40))))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("newPassword"));
        verify(accountWriter, never()).replacePassword(any(), any(), any());
    }

    // Both BCrypt steps run before UserAccountWriter opens the transaction that
    // stores the new hash (and ends every session); they used to run inside it.
    @Test
    void changePassword_success_hashesFirst_thenReplacesTheVerifiedHash() {
        signedInUserWithHash("old-hash");
        when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("n3w-secret")).thenReturn("new-hash");

        userService.changePassword(passwordChange("old-pass", "n3w-secret"));

        InOrder order = inOrder(passwordEncoder, accountWriter);
        order.verify(passwordEncoder).matches("old-pass", "old-hash");
        order.verify(passwordEncoder).encode("n3w-secret");
        order.verify(accountWriter).replacePassword(USER_ID, "old-hash", "new-hash");
    }

    // ---- deletion and updates ---------------------------------------------

    @Test
    void deleteUser_someoneElse_isDeniedBeforeAnyLookup() {
        doThrow(new AccessDeniedException("nope")).when(authUtils).requireSelfOrAdmin(USER_ID);

        assertThatThrownBy(() -> userService.deleteUser(USER_ID)).isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void deleteUser_missing_is404() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(USER_ID)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUser_endsSessionsBeforeDeleting() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.deleteUser(USER_ID);

        // The refresh-token rows reference the user, so they must go first.
        InOrder order = inOrder(refreshTokenService, userRepository);
        order.verify(refreshTokenService).endAllSessions(USER_ID);
        order.verify(userRepository).delete(user);
    }

    // Regression: the account's orders were deleted with it, and the stock of the
    // open ones never came back. Other modules now settle their rows first.
    @Test
    void deleteUser_announcesTheDeletionBeforeDeleting() {
        User user = new User();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.deleteUser(USER_ID);

        InOrder order = inOrder(events, userRepository);
        order.verify(events).publishEvent(new UserDeletingEvent(USER_ID));
        order.verify(userRepository).delete(user);
    }

    @Test
    void updateUserAndConvert_changesOnlyTheName() {
        User user = new User();
        user.setEmail("ada@example.com");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFirstName("Grace");
        request.setLastName("Hopper");

        userService.updateUserAndConvert(request, USER_ID);

        verify(authUtils).requireSelfOrAdmin(USER_ID);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("Grace");
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void getUserDtoById_someoneElse_isDenied() {
        doThrow(new AccessDeniedException("nope")).when(authUtils).requireSelfOrAdmin(USER_ID);

        assertThatThrownBy(() -> userService.getUserDtoById(USER_ID)).isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findById(any());
    }

    // ---- the signed-in user -----------------------------------------------

    @Test
    void getAuthenticatedUser_withNoAuthentication_failsAs401NotNpe() {
        SecurityContextHolder.clearContext();

        // AuthenticationFailedException is a 401; the NPE it replaces was a 500.
        assertThatThrownBy(() -> userService.getAuthenticatedUser())
                .isInstanceOf(AuthenticationFailedException.class);
        verify(userRepository, never()).findByEmailWithRoles(any());
    }

    // ---- helpers ----------------------------------------------------------

    private User signedInUserWithHash(String hash) {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword(hash);
        when(authUtils.currentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private static CreateUserRequest registration(String password) {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setEmail("ada@example.com");
        request.setPassword(password);
        return request;
    }

    private static ChangePasswordRequest passwordChange(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }
}
