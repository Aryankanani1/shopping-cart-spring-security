package com.aryan.spring_security_demo.security.config;

import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.security.jwt.AuthTokenFilter;
import com.aryan.spring_security_demo.security.jwt.JwtEntryPoint;
import com.aryan.spring_security_demo.security.user.UserDetailsService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class shopConfig {

  private final UserDetailsService userDetailsService;
  private final JwtEntryPoint jwtEntryPoint;

  private static final List<String> SECURED_URLS =
          List.of("/api/v1/carts/**", "/api/v1/cartItems/**", "/api/v1/orders/**");



    @Bean
    public ModelMapper modelMapper()
    {
        ModelMapper modelMapper = new ModelMapper();

        // Order field names don't match OrderDto, so map them explicitly.
        modelMapper.typeMap(Order.class, OrderDto.class).addMappings(mapper -> {
            mapper.map(Order::getLocalDate, OrderDto::setOrderDate);
            mapper.map(Order::getOrderStatus, OrderDto::setStatus);
            mapper.map(Order::getOrderItems, OrderDto::setItems);
        });

        // Product.imageList -> ProductDto.images (needed for nested cart mapping).
        modelMapper.typeMap(Product.class, ProductDto.class).addMappings(mapper ->
            mapper.map(Product::getImageList, ProductDto::setImages));

        // Image field names don't match ImageDto, so map them explicitly.
        modelMapper.typeMap(Image.class, ImageDto.class).addMappings(mapper -> {
            mapper.map(Image::getId, ImageDto::setImageId);
            mapper.map(Image::getFileName, ImageDto::setImageName);
            mapper.map(Image::getURL, ImageDto::setDownloadUrl);
        });

        return modelMapper;
    }


    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }


    @Bean
    public AuthTokenFilter authTokenFilter(){
        return new AuthTokenFilter();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws
            Exception {
     return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(){
        var authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
            http.csrf(AbstractHttpConfigurer::disable)
                    .exceptionHandling(exception ->exception.authenticationEntryPoint(jwtEntryPoint))
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth ->auth.requestMatchers(SECURED_URLS.toArray(String[]::new))
                            .authenticated().anyRequest().permitAll());
                    http.authenticationProvider(daoAuthenticationProvider());
                    http.addFilterBefore(authTokenFilter(), UsernamePasswordAuthenticationFilter.class);
                    return http.build();

    }

}
