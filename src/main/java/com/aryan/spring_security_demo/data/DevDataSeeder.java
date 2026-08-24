package com.aryan.spring_security_demo.data;

import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.RoleRepository;
import com.aryan.spring_security_demo.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds convenience test users and admins for local development.
 *
 * <p>This bean only exists under the {@code dev} profile — the profile decides
 * whether it is created, so there are no {@code if (isProd)} checks anywhere. In
 * production the bean is simply absent and no test accounts are created.
 *
 * <p>Runs after {@link DataInitializer} ({@code @Order(2)}) so the roles it
 * assigns are already committed.
 */
@Transactional
@Component
@RequiredArgsConstructor
@Order(2)
@Profile("dev")
@Slf4j
public class DevDataSeeder implements ApplicationListener<ApplicationReadyEvent> {

    // Matches hibernate.jdbc.batch_size so the persistence context is flushed and
    // cleared on the same boundary Hibernate uses to send JDBC batches, keeping
    // memory bounded during bulk inserts (avoids a bloated first-level cache).
    private static final int BATCH_SIZE = 20;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        createDefaultUsersIfNotExists();
        createDefaultAdminsIfNotExists();
    }

    private void createDefaultUsersIfNotExists() {
        Role userRole = roleRepository.findByName("ROLE_CUSTOMER").orElseThrow();
        for (int i = 1; i <= 5; i++) {
            String defaultEmail = "user" + i + "@gmail.com";
            if (userRepository.existsByEmail(defaultEmail)) {
                continue;
            }
            User user = new User();
            user.setFirstName("TestUser");
            user.setLastName("" + i);
            user.setEmail(defaultEmail);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setRoles(Set.of(userRole));
            userRepository.save(user);
            log.info("Default test user {} created successfully", i);

            if (i % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void createDefaultAdminsIfNotExists() {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        for (int i = 1; i <= 2; i++) {
            String defaultEmail = "admin" + i + "@gmail.com";
            if (userRepository.existsByEmail(defaultEmail)) {
                continue;
            }
            User user = new User();
            user.setFirstName("TestAdmin");
            user.setLastName("Admin" + i);
            user.setEmail(defaultEmail);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setRoles(Set.of(adminRole));
            userRepository.save(user);
            log.info("Default admin {} created successfully", i);

            if (i % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }
}
