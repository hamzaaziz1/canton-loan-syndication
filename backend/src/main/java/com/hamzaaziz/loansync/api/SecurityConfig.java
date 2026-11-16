package com.hamzaaziz.loansync.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.List;

/**
 * People log in to their own organisation's instance, and each person acts as
 * exactly one of that instance's parties. Development users share a password
 * from infra/.env. In production the organisation's identity provider issues
 * the credential, and the Authorization header carries its bearer token instead.
 */
@Configuration
@EnableConfigurationProperties(SecurityConfig.UiUsers.class)
public class SecurityConfig {

    static final String PARTY_AUTHORITY = "PARTY_";

    @ConfigurationProperties("loansync")
    public record UiUsers(List<UiUser> users) {
        public record UiUser(String name, String party) {}
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // a bare 401 with no Basic challenge, so browsers show the UI's own login rather than a popup
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // stateless: credentials travel in the Authorization header on every request and no session
                // cookie exists, so there is nothing for cross-site request forgery to ride on
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UiUsers config, PasswordEncoder encoder, @Value("${UI_DEV_PASSWORD}") String password) {
        var hashed = encoder.encode(password);
        return new InMemoryUserDetailsManager(config.users().stream()
                .map(u -> User.withUsername(u.name()).password(hashed).authorities(PARTY_AUTHORITY + u.party()).build())
                .toList());
    }
}
