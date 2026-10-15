package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.common.exception.AlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The account writes UserService makes after hashing: saving a new account, and
 * replacing a password hash only while it is still the one that was verified.
 */
@ExtendWith(MockitoExtension.class)
class UserAccountWriterTest {

    private static final Long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private ModelMapper modelMapper;

    @InjectMocks private UserAccountWriter writer;

    @Test
    void createAccount_savesTheGivenHash_asACustomer() {
        Role customer = new Role("ROLE_CUSTOMER");
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_CUSTOMER")).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        writer.createAccount(registration(), "hashed");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getRoles()).containsExactly(customer);
    }

    @Test
    void createAccount_emailTakenSinceUserServiceChecked_isRejected() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> writer.createAccount(registration(), "hashed"))
                .isInstanceOf(AlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void replacePassword_storesTheNewHash_andEndsEverySession() {
        User user = userWithHash("old-hash");

        writer.replacePassword(USER_ID, "old-hash", "new-hash");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(refreshTokenService).endAllSessions(USER_ID);
    }

    // The current password was checked, with no transaction held, against the hash
    // read then. If the password changed before this transaction, that check no
    // longer holds, so nothing is overwritten.
    @Test
    void replacePassword_hashChangedSinceItWasVerified_changesNothing() {
        User user = userWithHash("changed-meanwhile");

        assertThatThrownBy(() -> writer.replacePassword(USER_ID, "old-hash", "new-hash"))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("currentPassword"));
        assertThat(user.getPassword()).isEqualTo("changed-meanwhile");
        verify(refreshTokenService, never()).endAllSessions(any());
    }

    private User userWithHash(String hash) {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword(hash);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private static CreateUserRequest registration() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("ada@example.com");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setPassword("secret123");
        return request;
    }
}
