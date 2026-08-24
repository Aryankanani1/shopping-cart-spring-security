package com.aryan.spring_security_demo.data;

import com.aryan.spring_security_demo.model.Role;
import com.aryan.spring_security_demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds the default <b>roles</b> the application needs to function. Roles are
 * reference data required in <em>every</em> environment (auth depends on
 * {@code ROLE_ADMIN} / {@code ROLE_CUSTOMER}), so this bean is not profile-gated.
 *
 * <p>Test users/admins are environment-specific and live in
 * {@link DevDataSeeder} ({@code @Profile("dev")}), which runs after this
 * ({@code @Order(2)}) so the roles it assigns already exist.
 */
@Transactional
@Component
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final RoleRepository roleRepository;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Set<String> defaultRoles = Set.of("ROLE_ADMIN", "ROLE_CUSTOMER");
        createDefaultRoleIfNotExists(defaultRoles);
    }

    private void createDefaultRoleIfNotExists(Set<String> roles) {
        roles.stream()
                .filter(role -> roleRepository.findByName(role).isEmpty())
                .map(Role::new)
                .forEach(roleRepository::save);
        log.info("Default roles ensured: {}", roles);
    }
}
