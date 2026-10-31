package com.student.studentmanagement;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean registrationEnabled;

    public AuthController(
            AuthService authService,
            @Value("${app.registration.enabled:true}") boolean registrationEnabled) {

        this.authService = authService;
        this.registrationEnabled = registrationEnabled;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Public registration is disabled.");
        }

        String result = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request) {

        String token = authService.login(
                request.getUsername(),
                request.getPassword()
        );

        return ResponseEntity.ok(token);
    }
}


