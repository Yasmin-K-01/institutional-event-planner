package com.example.taskmanager.config;

import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User admin = userRepository.findByUsername("admin")
                .orElseGet(User::new);

        if (admin.getUsername() == null) {
            admin.setUsername("admin");
        }
        admin.setEmail("admin@example.com");
        admin.setPassword(passwordEncoder.encode("elite@admin"));
        admin.setRole("ADMIN");

        userRepository.save(admin);
        System.out.println(">>> ADMIN USER CREATED/UPDATED: admin / elite@admin <<<");
    }
}
