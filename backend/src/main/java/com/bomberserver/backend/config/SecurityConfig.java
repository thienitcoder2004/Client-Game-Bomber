package com.bomberserver.backend.config;

import com.bomberserver.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class cấu hình bảo mật chính cho Spring Security.
 *
 * Chức năng:
 * - Mã hóa password bằng BCrypt
 * - Cấu hình CORS cho frontend
 * - Tắt session vì dùng JWT
 * - Quy định endpoint nào public, endpoint nào cần đăng nhập
 * - Thêm JwtAuthenticationFilter vào chuỗi filter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Lấy danh sách domain frontend được phép gọi API từ file cấu hình.
     *
     * Ví dụ:
     * app.frontend.allowed-origins=http://localhost:3000,https://your-app.vercel.app,https://*.vercel.app
     */
    @Value("${app.frontend.allowed-origins}")
    private String allowedOriginsProperty;

    /**
     * Bean mã hóa password.
     *
     * BCrypt là kiểu mã hóa password phổ biến và an toàn,
     * dùng để hash mật khẩu trước khi lưu database.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cấu hình CORS để frontend được phép gọi backend.
     *
     * Vì frontend và backend thường khác domain/port
     * nên phải mở CORS đúng cách.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Tách chuỗi domain thành list, loại bỏ khoảng trắng và chuỗi rỗng
        List<String> allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toList());

        /**
         * Dùng allowed origin patterns thay vì allowed origins
         * để hỗ trợ domain động, ví dụ preview của Vercel:
         * https://abc-xyz.vercel.app
         */
        config.setAllowedOriginPatterns(allowedOrigins);

        // Các HTTP method cho phép frontend gọi
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Cho phép mọi header
        config.setAllowedHeaders(List.of("*"));

        // Header được phép frontend đọc sau khi response trả về
        config.setExposedHeaders(List.of("Authorization"));

        // Cho phép gửi cookie / thông tin xác thực nếu cần
        config.setAllowCredentials(true);

        // Áp dụng cấu hình CORS cho toàn bộ endpoint
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    /**
     * Cấu hình chuỗi filter bảo mật chính.
     *
     * @param http đối tượng HttpSecurity của Spring Security
     * @param jwtAuthenticationFilter filter tự viết để đọc JWT từ request
     * @return SecurityFilterChain
     * @throws Exception nếu lỗi cấu hình
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                // Bật CORS theo bean đã cấu hình ở trên
                .cors(Customizer.withDefaults())

                // Tắt CSRF vì backend đang dùng JWT stateless
                .csrf(csrf -> csrf.disable())

                /**
                 * Không dùng session server-side.
                 * Mỗi request sẽ tự mang JWT lên để xác thực.
                 */
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /**
                 * Cấu hình phân quyền cho các endpoint.
                 */
                .authorizeHttpRequests(auth -> auth
                        // Các API auth như login/register/forgot-password cho phép public
                        .requestMatchers("/api/auth/**").permitAll()

                        // WebSocket endpoint cho phép vào, vì auth có thể xử lý riêng bằng token query param
                        .requestMatchers("/ws/**").permitAll()

                        // Cho phép preflight request của CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Chỉ ADMIN mới được vào nhóm API admin
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Các request còn lại bắt buộc phải đăng nhập
                        .anyRequest().authenticated()
                ))

                /**
                 * Thêm filter JWT vào trước UsernamePasswordAuthenticationFilter.
                 * Nghĩa là JWT sẽ được kiểm tra trước khi Spring xử lý auth mặc định.
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}