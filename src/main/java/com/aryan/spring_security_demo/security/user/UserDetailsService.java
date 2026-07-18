package com.aryan.spring_security_demo.security.user;

import com.aryan.spring_security_demo.exception.UserNoFoundException;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =  Optional.ofNullable(userRepository.findByEmail(email)).orElseThrow(() ->
                new UserNoFoundException("User not found"));
        return com.aryan.spring_security_demo.security.user.UserDetails.buildUserDetails(user);
    }
}
