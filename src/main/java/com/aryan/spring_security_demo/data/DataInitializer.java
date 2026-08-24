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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
@Transactional
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {

    // Should match hibernate.jdbc.batch_size so the persistence context is flushed
    // and cleared on the same boundary Hibernate uses to send JDBC batches. This
    // keeps memory bounded during bulk inserts and avoids a bloated first-level cache.
    // IN SHORT, this logic prevents outOfMemory exception
    private static final int BATCH_SIZE = 20;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    @PersistenceContext
    private EntityManager entityManager;
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Set<String> defaultRoles = Set.of("ROLE_ADMIN","ROLE_CUSTOMER");
            createDefaultRoleIfNotExists(defaultRoles);
            createDefaultUserIfNotExists();
            createDefaultAdminIfNotExists();
    }

    private void createDefaultUserIfNotExists(){
        Role userRole = roleRepository.findByName("ROLE_CUSTOMER").get();
        for(int i= 1;i<=5;i++){
            String defaultEmail = "user"+i+"@gmail.com";
            if(userRepository.existsByEmail(defaultEmail)){
                continue;
            }
            User user  = new User();
            user.setFirstName("TestUser");
            user.setLastName(""+i);
            user.setEmail(defaultEmail);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setRoles(Set.of(userRole));
            userRepository.save(user);
            log.info("Default test user {} created successfully", i);

            // Batch insert: flush + clear on the batch boundary to bound memory
            // and let Hibernate group the INSERTs into JDBC batches.
            if (i % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void createDefaultAdminIfNotExists(){
        Role userRole = roleRepository.findByName("ROLE_ADMIN").get();
        for(int i= 1;i<=2;i++){
            String defaultEmail = "admin"+i+"@gmail.com";
            if(userRepository.existsByEmail(defaultEmail)){
                continue;
            }
            User user  = new User();
            user.setFirstName("TestAdmin");
            user.setLastName("Admin"+i);
            user.setEmail(defaultEmail);
            user.setPassword(passwordEncoder.encode("123456"));
            user.setRoles(Set.of(userRole));
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


    private void createDefaultRoleIfNotExists(Set<String> roles){
            roles.stream()
                    .filter(role -> roleRepository.findByName(role).isEmpty())
                    .map(Role:: new).forEach(roleRepository::save);
    }

}
