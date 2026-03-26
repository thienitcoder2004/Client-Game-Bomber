package com.bomberserver.backend.config;

import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Class cấu hình dùng để tự động tạo sẵn tài khoản ADMIN mặc định
 * khi backend khởi động.
 *
 * Mục đích:
 * - Nếu chưa có admin theo email cấu hình trong application.properties
 *   thì sẽ tạo mới.
 * - Nếu đã có rồi thì cập nhật lại thông tin cần thiết.
 *
 * Lưu ý:
 * - Thông tin admin mặc định lấy từ AdminDefaultProperties
 *   với prefix app.admin
 */
@Configuration
public class AdminBootstrapConfig {

    /**
     * Bean CommandLineRunner sẽ tự chạy sau khi Spring Boot khởi tạo xong.
     *
     * @param userAccountRepository repository thao tác với collection user account
     * @param passwordEncoder mã hóa password bằng BCrypt
     * @param adminDefaultProperties chứa cấu hình admin mặc định từ file properties
     * @return CommandLineRunner
     */
    @Bean
    CommandLineRunner adminBootstrap(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AdminDefaultProperties adminDefaultProperties
    ) {
        return args -> {
            // Nếu cấu hình tắt admin mặc định thì không làm gì nữa
            if (!adminDefaultProperties.isEnabled()) {
                System.out.println("Default admin is disabled.");
                return;
            }

            // Lấy email và password admin từ file cấu hình
            String email = adminDefaultProperties.getEmail();
            String rawPassword = adminDefaultProperties.getPassword();

            // Kiểm tra nếu thiếu email hoặc password thì dừng
            if (email == null || email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                System.out.println("Default admin config thiếu email hoặc password.");
                return;
            }

            // Chuẩn hóa email: bỏ khoảng trắng đầu/cuối và chuyển về chữ thường
            String normalizedEmail = email.trim().toLowerCase();

            // Tìm admin theo email
            // Nếu chưa có thì tạo object mới
            UserAccountDocument admin = userAccountRepository
                    .findByEmailIgnoreCase(normalizedEmail)
                    .orElseGet(UserAccountDocument::new);

            // Gán lại email chuẩn hóa
            admin.setEmail(normalizedEmail);

            // Mã hóa password trước khi lưu vào DB
            admin.setPasswordHash(passwordEncoder.encode(rawPassword));

            // Gán quyền ADMIN
            admin.setRole("ADMIN");

            // Kích hoạt tài khoản
            admin.setActive(true);

            /**
             * Nếu tài khoản chưa có username thì tự gán.
             * - Ưu tiên dùng "admin"
             * - Nếu "admin" đã tồn tại thì dùng "admin_system"
             */
            if (admin.getUsername() == null || admin.getUsername().isBlank()) {
                String preferredUsername = "admin";
                boolean usernameTaken = userAccountRepository.existsByUsernameIgnoreCase(preferredUsername);
                admin.setUsername(usernameTaken ? "admin_system" : preferredUsername);
            }

            // Nếu chưa có thời gian tạo thì set thời gian hiện tại
            if (admin.getCreatedAt() == null) {
                admin.setCreatedAt(Instant.now());
            }

            // Lưu admin vào database
            userAccountRepository.save(admin);

            // In log ra console để biết admin đã sẵn sàng
            System.out.println("Default admin ready: " + normalizedEmail);
        };
    }
}