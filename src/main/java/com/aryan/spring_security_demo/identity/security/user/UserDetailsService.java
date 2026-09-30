package com.aryan.spring_security_demo.identity.security.user;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserNotFoundException;
import com.aryan.spring_security_demo.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =  userRepository.findByEmailWithRoles(email).orElseThrow(() ->
                new UserNotFoundException("User not found"));
        return com.aryan.spring_security_demo.identity.security.user.UserDetails.buildUserDetails(user);
    }
}
