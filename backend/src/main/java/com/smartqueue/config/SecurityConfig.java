package com.smartqueue.config;

import com.smartqueue.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))

                .authorizeHttpRequests(auth -> auth

                        // ========================
                        // 🔓 PUBLIC ENDPOINTS
                        // ========================
                        .requestMatchers("/v1/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // Queue (public kiosk access)
                        .requestMatchers(HttpMethod.GET, "/v1/queue/branch/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/queue/tokens").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/queue/tokens/*/cancel").permitAll()

                        // Branches and Appointments (public booking access)
                        .requestMatchers(HttpMethod.GET, "/v1/branches").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/branches/*/services").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/appointments/slots").permitAll()

                        // ========================
                        // 🔒 BRANCH (PROTECTED)
                        // ========================
                        .requestMatchers(HttpMethod.GET, "/v1/branches/*/dashboard")
                        .hasAnyRole("SUPER_ADMIN", "BRANCH_ADMIN", "STAFF", "COUNTER_AGENT", "ADMIN")

                        .requestMatchers(HttpMethod.GET, "/v1/branches/**")
                        .hasAnyRole("SUPER_ADMIN", "BRANCH_ADMIN", "STAFF", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/v1/branches/**")
                        .hasRole("SUPER_ADMIN")

                        .requestMatchers(HttpMethod.PUT, "/v1/branches/**")
                        .hasAnyRole("SUPER_ADMIN", "BRANCH_ADMIN")

                        // ========================
                        // 🔒 COUNTERS
                        // ========================
                        .requestMatchers("/v1/counters/**")
                        .hasAnyRole("SUPER_ADMIN", "BRANCH_ADMIN", "STAFF", "COUNTER_AGENT")

                        // ========================
                        // 🔒 APPOINTMENTS (FIXED)
                        // ========================
                        .requestMatchers(HttpMethod.GET, "/v1/appointments/**")
                        .hasAnyRole("SUPER_ADMIN", "BRANCH_ADMIN", "STAFF")

                        .requestMatchers(HttpMethod.POST, "/v1/appointments/**")
                        .permitAll() // allow booking

                        // ========================
                        // 🔐 FALLBACK
                        // ========================
                        .anyRequest().authenticated())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ========================
    // 🔐 AUTH ENTRY POINT (401)
    // ========================
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("""
                        {"success":false,"message":"Unauthorized access"}
                    """);
        };
    }

    // ========================
    // 🚫 ACCESS DENIED (403)
    // ========================
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("""
                        {"success":false,"message":"You do not have permission to perform this action"}
                    """);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ========================
    // 🌐 CORS CONFIG
    // ========================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}