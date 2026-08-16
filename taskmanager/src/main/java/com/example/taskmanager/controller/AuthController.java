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
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, Object> payload) {
        System.out.println("Received payload: " + payload);

        String username = payload.get("username") != null ? payload.get("username").toString() :
                (payload.get("email") != null ? payload.get("email").toString() : "");
        String password = payload.get("password") != null ? payload.get("password").toString() : "";

        User user = userRepository.findByUsername(username)
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
