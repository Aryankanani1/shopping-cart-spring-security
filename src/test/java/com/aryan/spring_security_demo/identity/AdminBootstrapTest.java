package com.aryan.spring_security_demo.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The first-admin bootstrap. Repositories and the encoder are mocked. */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String EMAIL = "owner@example.com";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private final AdminBootstrapProperties properties = new AdminBootstrapProperties();
    private final Role admin = new Role("ROLE_ADMIN");
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(properties, userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void noEmail_doesNothing() {
        bootstrap.onApplicationEvent(null);

        verifyNoInteractions(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void noAccountYet_createsItAsAnAdminWithTheHashedPassword() {
        configure(EMAIL, "a-long-admin-password");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailWithRoles(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-admin-password")).thenReturn("hashed");

        bootstrap.onApplicationEvent(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getRoles()).containsExactly(admin);
    }

    @Test
    void noAccountAndNoPassword_stopsStartupWithoutCreatingAnything() {
        configure(EMAIL, "");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailWithRoles(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bootstrap.onApplicationEvent(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD is required");
        verify(userRepository, never()).save(any());
    }

    @Test
    void existingCustomer_getsTheAdminRoleAndKeepsTheirPassword() {
        configure(EMAIL, "a-long-admin-password");
        User customer = user(new Role("ROLE_CUSTOMER"));
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailWithRoles(EMAIL)).thenReturn(Optional.of(customer));

        bootstrap.onApplicationEvent(null);

        assertThat(customer.getRoles()).contains(admin);
        assertThat(customer.getPassword()).isEqualTo("their-own-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void existingAdmin_isLeftAsItIs() {
        configure(EMAIL, "");  // the password may be gone from the environment by now
        User existing = user(admin);
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmailWithRoles(EMAIL)).thenReturn(Optional.of(existing));

        bootstrap.onApplicationEvent(null);

        assertThat(existing.getRoles()).containsExactly(admin);
        verify(userRepository, never()).save(any());
    }

    private void configure(String email, String password) {
        properties.setEmail(email);
        properties.setPassword(password);
    }

    private static User user(Role role) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPassword("their-own-hash");
        user.getRoles().add(role);
        return user;
    }
}
