package com.college.clubmanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@clubmanagement.com}")
    private String fromEmail;

    @Value("${app.name:Club Management System}")
    private String appName;

    public void sendNotificationEmail(String to, String subject, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(fromEmail);
            mailMessage.setTo(to);
            mailMessage.setSubject(subject + " - " + appName);
            mailMessage.setText(message);

            mailSender.send(mailMessage);
        } catch (Exception e) {
            // Log error
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
            throw e;
        }
    }

    public void sendWelcomeEmail(String to, String username) {
        String subject = "Welcome to " + appName;
        String message = "Hello " + username + ",\n\n" +
                "Welcome to " + appName + "!\n\n" +
                "You can now browse clubs, join events, and connect with your college community.\n\n" +
                "Best regards,\n" +
                appName + " Team";

        sendNotificationEmail(to, subject, message);
    }

    public void sendPasswordResetEmail(String to, String resetToken) {
        String subject = "Password Reset Request";
        String resetUrl = "http://localhost:5173/reset-password?token=" + resetToken;
        String message = "You requested a password reset.\n\n" +
                "Click the link below to reset your password:\n" +
                resetUrl + "\n\n" +
                "This link will expire in 1 hour.\n\n" +
                "If you didn't request this, please ignore this email.\n\n" +
                "Best regards,\n" +
                appName + " Team";

        // Always log to console for development
        System.out.println("\n========================================");
        System.out.println("🔐 PASSWORD RESET REQUEST");
        System.out.println("========================================");
        System.out.println("📧 Email: " + to);
        System.out.println("🔑 Token: " + resetToken);
        System.out.println("🔗 Reset URL: " + resetUrl);
        System.out.println("========================================\n");

        try {
            sendNotificationEmail(to, subject, message);
            System.out.println("✅ Email sent successfully to: " + to);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to send email: " + e.getMessage());
            System.err.println("💡 Copy the URL above and paste it in your browser to reset password.");
        }
    }
}
