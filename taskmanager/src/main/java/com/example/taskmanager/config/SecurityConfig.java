package com.example.taskmanager.config;

import com.example.taskmanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;
    private final UserRepository userRepository;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final String rememberMeKey;

    public SecurityConfig(AuthTokenFilter authTokenFilter, UserRepository userRepository,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          @Value("${app.security.remember-me-key}") String rememberMeKey) {
        this.authTokenFilter = authTokenFilter;
        this.userRepository = userRepository;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.rememberMeKey = rememberMeKey;
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .map(appUser -> User.builder()
                        .username(appUser.getUsername())
                        .password(appUser.getPassword())
                        .roles(resolveRole(appUser.getRole()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(rememberMeKey, userDetailsService);
        services.setTokenValiditySeconds(7 * 24 * 60 * 60);
        services.setUseSecureCookie(true);
        return services;
    }

    private String resolveRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RememberMeServices rememberMeServices,
                                            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/events/live-sync", "/index.html", "/", "/css/**", "/js/**", "/ws-task/**", "/static/**").permitAll()
                .anyRequest().permitAll()
            )
            .rememberMe(remember -> remember
                    .rememberMeServices(rememberMeServices)
                    .tokenValiditySeconds(7 * 24 * 60 * 60));

        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler));
        }

        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
