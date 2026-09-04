package com.aryan.spring_security_demo.security.config;

import com.aryan.spring_security_demo.dto.CartDto;
import com.aryan.spring_security_demo.dto.CartItemDto;
import com.aryan.spring_security_demo.dto.ImageDto;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.ProductDto;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.CartItem;
import com.aryan.spring_security_demo.model.Image;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.security.ApiAccessDeniedHandler;
import com.aryan.spring_security_demo.security.jwt.AuthTokenFilter;
import com.aryan.spring_security_demo.security.jwt.JwtEntryPoint;
import com.aryan.spring_security_demo.security.user.UserDetailsService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpMethod;
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

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class ShopConfig {

  private final UserDetailsService userDetailsService;
  private final JwtEntryPoint jwtEntryPoint;
  private final ApiAccessDeniedHandler apiAccessDeniedHandler;


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

        // Cart/CartItem id fields don't match the DTOs, so map them explicitly.
        modelMapper.typeMap(Cart.class, CartDto.class).addMappings(mapper ->
            mapper.map(Cart::getId, CartDto::setCartId));
        modelMapper.typeMap(CartItem.class, CartItemDto.class).addMappings(mapper ->
            mapper.map(CartItem::getId, CartItemDto::setItemId));

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
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint(jwtEntryPoint)          // 401 — unauthenticated
                            .accessDeniedHandler(apiAccessDeniedHandler))     // 403 — authenticated, wrong authority
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // Deny by default: only the endpoints listed below are public, so
                    // adding a new controller can never accidentally expose it. This
                    // closes the previous gap where /users/** (and catalog writes) were
                    // reachable with no authentication at all.
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/**").permitAll()                 // login
                            .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()  // self-registration
                            // Read-only catalog browsing is open to everyone.
                            .requestMatchers(HttpMethod.GET,
                                    "/api/v1/products/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/images/**").permitAll()
                            // API docs.
                            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                            .requestMatchers("/error").permitAll()
                            // Catalog writes are admin-only. These rules live here at the
                            // edge (not as @PreAuthorize on the controllers) so the whole
                            // access map is auditable in one place and business code stays
                            // free of security concerns. Order matters: these sit below the
                            // GET permitAll above (public reads) and above the catch-all —
                            // first match wins.
                            .requestMatchers(HttpMethod.POST,
                                    "/api/v1/products/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/images/**").hasAuthority("ROLE_ADMIN")
                            .requestMatchers(HttpMethod.PUT,
                                    "/api/v1/products/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/images/**").hasAuthority("ROLE_ADMIN")
                            .requestMatchers(HttpMethod.DELETE,
                                    "/api/v1/products/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/images/**").hasAuthority("ROLE_ADMIN")
                            // Everything else — carts, orders, user management — requires
                            // authentication; object-level ownership is then enforced in
                            // the service layer (see AuthUtils / CartService).
                            .anyRequest().authenticated());
                    http.authenticationProvider(daoAuthenticationProvider());
                    http.addFilterBefore(authTokenFilter(), UsernamePasswordAuthenticationFilter.class);
                    return http.build();

    }

}
