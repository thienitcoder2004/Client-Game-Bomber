package com.bomberserver.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Class dùng để map cấu hình từ application.properties / application.yml
 * với prefix: app.admin
 *
 * Ví dụ:
 * app.admin.enabled=true
 * app.admin.email=admin@gmail.com
 * app.admin.password=123456
 *
 * Spring sẽ tự động bind dữ liệu từ file cấu hình vào class này.
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminDefaultProperties {

    /**
     * Cho phép bật/tắt việc tạo admin mặc định khi app khởi động.
     * Mặc định là true.
     */
    private boolean enabled = true;

    /**
     * Email của admin mặc định.
     */
    private String email;

    /**
     * Password thô của admin mặc định.
     * Sau đó sẽ được mã hóa trước khi lưu DB.
     */
    private String password;

    /**
     * Kiểm tra có bật admin mặc định hay không.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set trạng thái bật/tắt admin mặc định.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Lấy email admin mặc định.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set email admin mặc định.
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Lấy password admin mặc định.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set password admin mặc định.
     */
    public void setPassword(String password) {
        this.password = password;
    }
}