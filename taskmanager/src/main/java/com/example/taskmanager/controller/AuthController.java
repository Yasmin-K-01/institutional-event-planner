package com.example.taskmanager.controller;

import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.service.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final RememberMeServices rememberMeServices;
    private final com.example.taskmanager.service.AuthService authService;

    public AuthController(UserRepository userRepository, PasswordEncoder encoder, JwtUtils jwtUtils,
                          RememberMeServices rememberMeServices,
                          com.example.taskmanager.service.AuthService authService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.rememberMeServices = rememberMeServices;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        User user = new User(signUpRequest.getUsername(), encoder.encode(signUpRequest.getPassword()));
        user.setEmail(signUpRequest.getEmail() == null ? signUpRequest.getUsername() : signUpRequest.getEmail().toLowerCase());
        user.setRole("USER");
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping({"/login", "/signin"})
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, Object> payload,
                                              HttpServletRequest request, HttpServletResponse response) {
        String username = payload.get("username") != null ? payload.get("username").toString().trim() :
                (payload.get("email") != null ? payload.get("email").toString().trim() : "");
        String password = payload.get("password") != null ? payload.get("password").toString().trim() : "";

        System.out.println("Admin Login attempt for user: " + username);

        User user = userRepository.findByUsername(username)
            .or(() -> userRepository.findByEmail(username))
            .orElse(null);

        if (user == null
                && ("admin".equalsIgnoreCase(username)
                || "admin@francisxavier.ac.in".equalsIgnoreCase(username))
                && "elite@admin".equals(password)) {
            user = new User(username, encoder.encode(password));
            user.setRole("ADMIN");
            user = userRepository.save(user);
        }

        if (user == null || !encoder.matches(password, user.getPassword())) {
            return ResponseEntity.badRequest().body("Error: Invalid username or password");
        }

        boolean rememberMe = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("rememberMe", false)));
        String jwt = jwtUtils.generateJwtToken(user.getUsername(), rememberMe ? 7L * 24 * 60 * 60 * 1000 : 24L * 60 * 60 * 1000);

        if (rememberMe) {
            String role = user.getRole() == null ? "USER" : user.getRole().replace("ROLE_", "");
            rememberMeServices.loginSuccess(request, response,
                new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        }

        Map<String, Object> loginResponse = new HashMap<>();
        loginResponse.put("token", jwt);
        loginResponse.put("username", user.getUsername());
        loginResponse.put("role", user.getRole());

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> payload) {
        authService.requestPasswordReset(payload.get("email"));
        return ResponseEntity.ok(Map.of("message", "If the email is registered, an OTP has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> payload) {
        try {
            authService.resetPassword(payload.get("email"), payload.get("otp"), payload.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<String>> getUsers() {
        List<String> usernames = userRepository.findAll().stream()
                .map(User::getUsername)
                .filter(username -> !username.toLowerCase().contains("admin"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(usernames);
    }
}
