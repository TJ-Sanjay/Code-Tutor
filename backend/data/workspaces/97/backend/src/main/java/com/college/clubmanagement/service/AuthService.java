package com.college.clubmanagement.service;

import com.college.clubmanagement.dto.JwtResponse;
import com.college.clubmanagement.dto.LoginRequest;
import com.college.clubmanagement.dto.RegisterRequest;
import com.college.clubmanagement.entity.Role;
import com.college.clubmanagement.entity.User;
import com.college.clubmanagement.exception.BadRequestException;
import com.college.clubmanagement.repository.UserRepository;
import com.college.clubmanagement.repository.PasswordResetTokenRepository;
import com.college.clubmanagement.entity.PasswordResetToken;
import com.college.clubmanagement.security.JwtTokenProvider;
import com.college.clubmanagement.util.PasswordValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private AuthenticationManager authenticationManager;

        @Autowired
        private JwtTokenProvider tokenProvider;

        @Autowired
        private EmailService emailService;

        @Autowired
        private PasswordResetTokenRepository passwordResetTokenRepository;

        @Transactional
        public JwtResponse register(RegisterRequest request) {
                if (userRepository.existsByUsername(request.getUsername())) {
                        throw new BadRequestException("Username is already taken");
                }

                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new BadRequestException("Email is already in use");
                }

                // Validate password strength
                PasswordValidator.ValidationResult passwordValidation = PasswordValidator
                                .validate(request.getPassword());
                if (!passwordValidation.isValid()) {
                        throw new BadRequestException(passwordValidation.getMessage());
                }

                User user = new User();
                user.setUsername(request.getUsername());
                user.setEmail(request.getEmail());
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setFirstName(request.getFirstName());
                user.setLastName(request.getLastName());
                user.setPhone(request.getPhone());
                user.setDepartment(request.getDepartment());
                user.setYear(request.getYear());

                Set<Role> roles = new HashSet<>();
                roles.add(Role.STUDENT);
                user.setRoles(roles);

                userRepository.save(user);

                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                String jwt = tokenProvider.generateToken(authentication);

                Set<String> roleNames = user.getRoles().stream()
                                .map(Role::name)
                                .collect(Collectors.toSet());

                return new JwtResponse(jwt, user.getId(), user.getUsername(),
                                user.getEmail(), user.getFullName(), roleNames);
        }

        public JwtResponse login(LoginRequest request) {
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                String jwt = tokenProvider.generateToken(authentication);

                User user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new BadRequestException("User not found"));

                Set<String> roleNames = user.getRoles().stream()
                                .map(Role::name)
                                .collect(Collectors.toSet());

                return new JwtResponse(jwt, user.getId(), user.getUsername(),
                                user.getEmail(), user.getFullName(), roleNames);
        }

        @Transactional
        public void forgotPassword(String email) {
                User user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new BadRequestException("No account found with this email"));

                // Delete any existing tokens for this user
                passwordResetTokenRepository.deleteByUser(user);

                // Generate new token
                String token = java.util.UUID.randomUUID().toString();

                PasswordResetToken resetToken = new PasswordResetToken();
                resetToken.setToken(token);
                resetToken.setUser(user);

                passwordResetTokenRepository.save(resetToken);

                // Send email
                emailService.sendPasswordResetEmail(user.getEmail(), token);
        }

        @Transactional
        public void resetPassword(String token, String newPassword) {
                PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

                if (resetToken.isUsed()) {
                        throw new BadRequestException("This reset token has already been used");
                }

                if (resetToken.isExpired()) {
                        throw new BadRequestException("This reset token has expired");
                }

                // Validate new password
                PasswordValidator.ValidationResult validation = PasswordValidator.validate(newPassword);
                if (!validation.isValid()) {
                        throw new BadRequestException(validation.getMessage());
                }

                // Update password
                User user = resetToken.getUser();
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                // Mark token as used
                resetToken.setUsed(true);
                passwordResetTokenRepository.save(resetToken);
        }
}
