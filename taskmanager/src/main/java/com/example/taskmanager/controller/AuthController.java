package com.example.taskmanager.controller;

import com.example.taskmanager.model.User;
import com.example.taskmanager.model.LoginRequest;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.service.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    public AuthController(UserRepository userRepository, PasswordEncoder encoder, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User signUpRequest) {
        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        User user = new User(signUpRequest.getUsername(), encoder.encode(signUpRequest.getPassword()));
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.getUsername())
                .orElse(null);

        // Auto-create admin user on first login with correct credentials
        if (user == null
                && ("admin".equalsIgnoreCase(loginRequest.getUsername())
                || "admin@francisxavier.ac.in".equalsIgnoreCase(loginRequest.getUsername()))
                && "elite@admin".equals(loginRequest.getPassword())) {
            user = new User(loginRequest.getUsername(), encoder.encode(loginRequest.getPassword()));
            user.setRole("ADMIN");  // ✅ CRITICAL: Set role to ADMIN
            user = userRepository.save(user);
        }

        if (user == null || !encoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("Error: Invalid username or password");
        }

        String jwt = jwtUtils.generateJwtToken(user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("username", user.getUsername());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
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
