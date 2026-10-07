package com.aryan.spring_security_demo.identity.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets Prometheus scrape {@code /actuator/prometheus}. Prometheus can't log in
 * for a JWT, so it sends HTTP Basic credentials ({@code basic_auth} in its scrape
 * config) for a dedicated scrape account ({@link PrometheusScrapeProperties}).
 *
 * <p>Only active when {@code app.metrics.prometheus.scrape-enabled} is true;
 * otherwise the endpoint is admin-only like the rest of the actuator
 * ({@code ShopConfig}). This chain matches the prometheus endpoint only and runs
 * before ShopConfig's, and its authentication manager knows only the scrape
 * account: the account can't be used anywhere else, and no customer or admin
 * credentials work here. Its password is held only as a hash.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "app.metrics.prometheus.scrape-enabled")
public class PrometheusScrapeSecurityConfig {

    static final String SCRAPER_AUTHORITY = "ROLE_METRICS_SCRAPER";

    @Bean
    @Order(1)
    public SecurityFilterChain prometheusScrapeFilterChain(HttpSecurity http,
                                                           PasswordEncoder passwordEncoder,
                                                           PrometheusScrapeProperties properties) throws Exception {
        var scraper = User.withUsername(properties.getUsername())
                .password(passwordEncoder.encode(properties.getPassword()))
                .authorities(SCRAPER_AUTHORITY)
                .build();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new InMemoryUserDetailsManager(scraper));
        provider.setPasswordEncoder(passwordEncoder);

        http.securityMatcher(EndpointRequest.to("prometheus"))
                // Only the scrape account; a 401 with a Basic challenge otherwise.
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(SCRAPER_AUTHORITY))
                .httpBasic(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationManager(new ProviderManager(provider));
        return http.build();
    }
}
