package com.aryan.spring_security_demo.identity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the account named by {@code ADMIN_EMAIL} is an admin, in every
 * profile, so a fresh production database has someone who can manage the
 * catalogue and the orders. Off while {@code ADMIN_EMAIL} is empty.
 *
 * <ul>
 *   <li>No account with that email: creates one with {@code ROLE_ADMIN}.
 *       {@code ADMIN_PASSWORD} is required for this, and startup fails without it.</li>
 *   <li>The account exists: adds {@code ROLE_ADMIN} if it lacks it, and leaves the
 *       password alone, so a restart never resets a password the admin changed.</li>
 * </ul>
 *
 * Every later start finds the admin and changes nothing, so {@code ADMIN_PASSWORD}
 * can be removed from the environment once the account exists. Runs after
 * {@link DataInitializer} ({@code @Order(1)}), which creates the roles.
 */
@Transactional
@Component
@RequiredArgsConstructor
@Order(3)
@Slf4j
public class AdminBootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        Role admin = roleRepository.findByName(ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(ADMIN_ROLE + " is missing"));
        String email = properties.getEmail().trim();
        userRepository.findByEmailWithRoles(email).ifPresentOrElse(
                user -> makeAdmin(user, admin),
                () -> createAdmin(email, admin));
    }

    private void makeAdmin(User user, Role admin) {
        if (user.getRoles().add(admin)) {
            log.info("Admin bootstrap: gave {} the admin role", user.getEmail());
        }
    }

    private void createAdmin(String email, Role admin) {
        String password = properties.getPassword();
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("ADMIN_PASSWORD is required to create the admin account " + email);
        }
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Shop");
        user.setLastName("Admin");
        user.setPassword(passwordEncoder.encode(password));
        user.getRoles().add(admin);
        userRepository.save(user);
        log.info("Admin bootstrap: created the admin account {}", email);
    }
}
