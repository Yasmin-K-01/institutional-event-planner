package com.example.taskmanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.email.enabled:true}") boolean emailEnabled) {
        this.mailSender = mailSender.getIfAvailable();
        this.emailEnabled = emailEnabled;
    }

    public void sendTaskNotification(String toEmail, String subject, String body) {
        // If email service is disabled, skip sending
        if (!emailEnabled) {
            logger.info("[OFFLINE MODE] Email sending skipped for: {}", toEmail);
            return;
        }
        if (mailSender == null) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Email notification skipped/failed: " + e.getMessage());
        }
    }

    public void sendPasswordResetOtp(String toEmail, String otp) {
        sendTaskNotification(toEmail, "EliteSchedule password reset OTP",
                "Your password reset OTP is " + otp + ". It expires in 10 minutes.");
    }
}
