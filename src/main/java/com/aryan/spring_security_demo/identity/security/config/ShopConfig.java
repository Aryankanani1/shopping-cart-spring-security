package com.aryan.spring_security_demo.identity.security.config;

import com.aryan.spring_security_demo.cart.Cart;
import com.aryan.spring_security_demo.cart.CartDto;
import com.aryan.spring_security_demo.cart.CartItem;
import com.aryan.spring_security_demo.cart.CartItemDto;
import com.aryan.spring_security_demo.catalog.Image;
import com.aryan.spring_security_demo.catalog.ImageDto;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductDto;
import com.aryan.spring_security_demo.identity.RateLimitProperties;
import com.aryan.spring_security_demo.identity.security.ApiAccessDeniedHandler;
import com.aryan.spring_security_demo.identity.security.jwt.AuthTokenFilter;
import com.aryan.spring_security_demo.identity.security.jwt.JwtEntryPoint;
import com.aryan.spring_security_demo.identity.security.ratelimit.RateLimitFilter;
import com.aryan.spring_security_demo.identity.security.ratelimit.RateLimitService;
import com.aryan.spring_security_demo.identity.security.user.UserDetailsService;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderDto;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RateLimitService rateLimitService,
                                                   RateLimitProperties rateLimitProperties,
                                                   @Value("${api.prefix}") String apiPrefix) throws Exception{
            // Throttle the auth endpoints before any authentication work runs. Built
            // here (not a @Component) so it isn't dragged into @WebMvcTest slices
            // without its collaborators — same pattern as authTokenFilter below.
            RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitService, rateLimitProperties, apiPrefix);

            http.csrf(AbstractHttpConfigurer::disable)
                    // CORS for the frontend origins in app.cors.allowed-origins, on the
                    // API paths only (CorsConfig); none are allowed by default. A
                    // preflight from a listed origin is answered here, before the
                    // authorization rules, because browsers send it without the token.
                    // It reveals nothing and changes nothing; the real request that
                    // follows still has to pass every rule below. (Spring Security
                    // would apply CorsConfig's bean on its own; it is spelled out so
                    // the whole access map stays readable in this one place.)
                    .cors(Customizer.withDefaults())
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint(jwtEntryPoint)          // 401 — unauthenticated
                            .accessDeniedHandler(apiAccessDeniedHandler))     // 403 — authenticated, wrong authority
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // Deny by default: only the endpoints listed below are public, so
                    // adding a new controller can never accidentally expose it. This
                    // closes the previous gap where /users/** (and catalog writes) were
                    // reachable with no authentication at all.
                    .authorizeHttpRequests(auth -> auth
                            // Password change is the one /auth endpoint that needs a
                            // signed-in caller (it lives under /auth to be rate-limited
                            // like login). Listed above the permitAll: first match wins.
                            .requestMatchers(HttpMethod.PUT, "/api/v1/auth/password").authenticated()
                            .requestMatchers("/api/v1/auth/**").permitAll()                 // login
                            .requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll()  // self-registration
                            // Read-only catalog browsing is open to everyone.
                            .requestMatchers(HttpMethod.GET,
                                    "/api/v1/products/**",
                                    "/api/v1/categories/**",
                                    "/api/v1/images/**").permitAll()
                            // API docs.
                            .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                            // Actuator rules match the endpoints themselves, not
                            // "/actuator/..." strings, so they keep working if the base
                            // path or the management port changes.
                            // The health probe (and its liveness/readiness groups) is
                            // public so load balancers and k8s can reach it. Anonymous
                            // callers and customers only get UP/DOWN; the details are for
                            // admins (management.endpoint.health.roles).
                            .requestMatchers(EndpointRequest.to("health")).permitAll()
                            // Every other actuator endpoint (info, metrics, the index)
                            // shows operational detail: JVM and request metrics, build
                            // and process info. That's admin-only, not for any signed-in
                            // customer. Must stay below the health rule.
                            .requestMatchers(EndpointRequest.toAnyEndpoint()).hasAuthority("ROLE_ADMIN")
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
                            // Advancing an order's status (PROCESSING/SHIPPED/DELIVERED)
                            // is a fulfillment action, so it is admin-only at the edge —
                            // like the catalog writes above. Cancellation is deliberately
                            // NOT here: it is a POST that a customer may perform on their
                            // own order, so its ownership rule lives in the service layer.
                            .requestMatchers(HttpMethod.PATCH,
                                    "/api/v1/orders/*/status").hasAuthority("ROLE_ADMIN")
                            // Listing every order (admin order management) is admin-only —
                            // without this it would fall through to "authenticated" and any
                            // logged-in user could enumerate all customers' orders.
                            .requestMatchers(HttpMethod.GET,
                                    "/api/v1/orders/admin").hasAuthority("ROLE_ADMIN")
                            // Everything else — carts, orders, user management — requires
                            // authentication; object-level ownership is then enforced in
                            // the service layer (see AuthUtils / CartService).
                            .anyRequest().authenticated());
                    http.authenticationProvider(daoAuthenticationProvider());
                    http.addFilterBefore(authTokenFilter(), UsernamePasswordAuthenticationFilter.class);
                    // Rate limiter sits ahead of authentication so an over-limit caller
                    // is rejected with 429 before any token/credential processing.
                    http.addFilterBefore(rateLimitFilter, AuthTokenFilter.class);
                    return http.build();

    }

}
