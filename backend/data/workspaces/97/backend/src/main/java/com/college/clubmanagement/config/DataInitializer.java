package com.college.clubmanagement.config;

import com.college.clubmanagement.entity.Role;
import com.college.clubmanagement.entity.User;
import com.college.clubmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository) {
        return args -> {
            // Create admin user if it doesn't exist
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setEmail("admin@clubhub.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setFirstName("System");
                admin.setLastName("Administrator");

                Set<Role> roles = new HashSet<>();
                roles.add(Role.ADMIN);
                admin.setRoles(roles);

                userRepository.save(admin);
                System.out.println("==> Admin user created: username='admin', password='admin123'");
            }
        };
    }
}
