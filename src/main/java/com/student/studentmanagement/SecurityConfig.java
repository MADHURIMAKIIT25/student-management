
package com.student.studentmanagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

 .authorizeHttpRequests(auth -> auth

    // Public frontend resources
    .requestMatchers(
        "/",
        "/index.html",
        "/css/**",
        "/js/**",
        "/images/**",
        "/favicon.ico"
    ).permitAll()

    // Public authentication endpoints
    .requestMatchers("/api/auth/**").permitAll()

    // Students can access their own profile
    .requestMatchers(
        HttpMethod.GET,
        "/api/students/me"
    ).hasRole("STUDENT")

    .requestMatchers(
        HttpMethod.PUT,
        "/api/students/me"
    ).hasRole("STUDENT")

    // Only administrators can manage all student records
    .requestMatchers(
        "/api/students",
        "/api/students/**"
    ).hasRole("ADMIN")

    .anyRequest().authenticated()
)

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    writeErrorResponse(
                        request,
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required or token is invalid"
                    )
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeErrorResponse(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource."
                    )
                )
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");

        String timestamp = jsonEscape(LocalDateTime.now().toString());
        String error = jsonEscape(status.getReasonPhrase());
        String safeMessage = jsonEscape(message);
        String path = jsonEscape(request.getRequestURI());

        String body = "{"
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + error + "\","
                + "\"message\":\"" + safeMessage + "\","
                + "\"path\":\"" + path + "\""
                + "}";

        response.getWriter().write(body);
    }

    private String jsonEscape(String value) {
        StringBuilder result = new StringBuilder();

        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }

        return result.toString();
    }
}

