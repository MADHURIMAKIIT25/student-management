package com.student.studentmanagement;

import com.student.studentmanagement.entity.AppUser;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            StudentRepository studentRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {

        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    // Register a new student account and profile together
    @Transactional
    public String register(RegisterRequest request) {

        String username = request.getUsername().trim();
        String email = request.getEmail().trim();

        if (appUserRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(
                    "Username already exists"
            );
        }

        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUsernameException(
                    "Email address is already registered"
            );
        }

        // Public registrations always receive STUDENT role.
        // Never accept a role from the registration request.
        AppUser user = new AppUser(
                username,
                passwordEncoder.encode(request.getPassword()),
                "STUDENT"
        );

        AppUser savedUser;

        try {
            savedUser = appUserRepository.saveAndFlush(user);

        } catch (DataIntegrityViolationException ex) {

            if (appUserRepository.existsByUsername(username)) {
                throw new DuplicateUsernameException(
                        "Username already exists"
                );
            }

            throw ex;
        }

        // Create the student's own profile.
        Student student = new Student(
                request.getName().trim(),
                email,
                request.getCourse().trim(),
                request.getAge()
        );

        String gender = request.getGender();

        if (gender != null && !gender.isBlank()) {
            student.setGender(gender.trim());
        }

        // Link the profile to the newly created account.
        student.setOwner(savedUser);

        studentRepository.saveAndFlush(student);

        return "Student account registered successfully";
    }

    // Authenticate an existing account
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
