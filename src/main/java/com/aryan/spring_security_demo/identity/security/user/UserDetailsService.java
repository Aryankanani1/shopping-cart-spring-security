package com.aryan.spring_security_demo.identity.security.user;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Throws Spring's {@link UsernameNotFoundException} for an unknown email, not a
     * domain exception: at login, {@code DaoAuthenticationProvider} turns that one
     * into the same {@code BadCredentialsException} (401) as a wrong password, so
     * the response can't be used to find out which emails have accounts. Any other
     * exception is treated as a server error (500).
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =  userRepository.findByEmailWithRoles(email).orElseThrow(() ->
                new UsernameNotFoundException("User not found"));
        return com.aryan.spring_security_demo.identity.security.user.UserDetails.buildUserDetails(user);
    }
}
