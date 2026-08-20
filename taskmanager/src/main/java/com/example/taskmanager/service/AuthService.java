package com.example.taskmanager.service;

import com.example.taskmanager.model.PasswordResetToken;
import com.example.taskmanager.model.User;
import com.example.taskmanager.repository.PasswordResetTokenRepository;
import com.example.taskmanager.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public User upsertGoogleUser(OAuth2User googleUser) {
        String email = googleUser.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google did not provide an email address");
        }
        String googleId = googleUser.getAttribute("sub");
        User user = (googleId == null ? java.util.Optional.<User>empty() : userRepository.findByGoogleId(googleId))
            .or(() -> userRepository.findByEmail(email))
            .or(() -> userRepository.findByUsername(email))
                .orElseGet(() -> new User(email, passwordEncoder.encode(secureRandom.nextLong() + "-google")));

        user.setUsername(user.getUsername() == null || user.getUsername().isBlank() ? email : user.getUsername());
        user.setEmail(email);
        user.setGoogleId(googleId);
        user.setDisplayName(googleUser.getAttribute("name"));
        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("USER");
        }
        return userRepository.save(user);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return;
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(normalizedEmail);
        token.setOtpHash(passwordEncoder.encode(otp));
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        token.setCreatedAt(Instant.now());
        token.setUsed(false);
        tokenRepository.save(token);
        emailService.sendPasswordResetOtp(normalizedEmail, otp);
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        if (otp == null || !otp.matches("\\d{6}") || newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("Invalid OTP or password");
        }
        PasswordResetToken token = tokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));
        if (token.getExpiresAt().isBefore(Instant.now()) || !passwordEncoder.matches(otp, token.getOtpHash())) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        token.setUsed(true);
        tokenRepository.save(token);
    }
}
