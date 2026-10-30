package com.student.studentmanagement;

import com.student.studentmanagement.entity.AppUser;
import com.student.studentmanagement.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public String register(String username, String password) {

       if (appUserRepository.existsByUsername(username)) {
    throw new DuplicateUsernameException("Username already exists");
}

        AppUser user = new AppUser(
                username,
                passwordEncoder.encode(password)
        );

        appUserRepository.save(user);

        return "User registered successfully";
    }

   public String login(String username, String password) {
    try {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                username,
                password
            )
        );

        return jwtService.generateToken(username);

    } catch (org.springframework.security.core.AuthenticationException ex) {
        throw new org.springframework.security.authentication.BadCredentialsException(
            "Invalid username or password"
        );
    }
}
}
