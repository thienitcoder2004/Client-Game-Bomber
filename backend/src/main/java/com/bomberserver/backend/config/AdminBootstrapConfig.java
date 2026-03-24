package com.bomberserver.backend.config;

import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@Configuration
public class AdminBootstrapConfig {

    @Bean
    CommandLineRunner adminBootstrap(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AdminDefaultProperties adminDefaultProperties
    ) {
        return args -> {
            if (!adminDefaultProperties.isEnabled()) {
                System.out.println("Default admin is disabled.");
                return;
            }

            String email = adminDefaultProperties.getEmail();
            String rawPassword = adminDefaultProperties.getPassword();

            if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                System.out.println("Default admin config thiếu email hoặc password.");
                return;
            }

            String normalizedEmail = email.trim().toLowerCase();

            UserAccountDocument admin = userAccountRepository
                    .findByEmailIgnoreCase(normalizedEmail)
                    .orElseGet(UserAccountDocument::new);

            admin.setEmail(normalizedEmail);
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));
            admin.setRole("ADMIN");
            admin.setActive(true);

            if (admin.getUsername() == null || admin.getUsername().isBlank()) {
                String preferredUsername = "admin";
                boolean usernameTaken = userAccountRepository.existsByUsernameIgnoreCase(preferredUsername);
                admin.setUsername(usernameTaken ? "admin_system" : preferredUsername);
            }

            if (admin.getCreatedAt() == null) {
                admin.setCreatedAt(Instant.now());
            }

            userAccountRepository.save(admin);

            System.out.println("Default admin ready: " + normalizedEmail);
        };
    }
}