package com.aryan.spring_security_demo.data;

import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationListener<ApplicationReadyEvent> {
    private final UserRepository userRepository;
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
            createDefaultUserIfNotExists();
    }

    private void createDefaultUserIfNotExists(){
        for(int i= 1;i<=5;i++){
            String defaultEmail = "user"+i+"@gmail.com";
            if(userRepository.existsByEmail(defaultEmail)){
                continue;
            }
            User user  = new User();
            user.setFirstName("TestUser");
            user.setLastName(""+i);
            user.setEmail(defaultEmail);
            user.setPassword("123456");
            userRepository.save(user);
            System.out.println("default vet user " + i + "created successfully");

        }
    }
}
