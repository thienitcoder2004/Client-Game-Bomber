package com.bomberserver.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service dùng để gửi email từ backend.
 *
 * Hiện tại class này phục vụ chức năng:
 * - gửi mã OTP đặt lại mật khẩu cho người dùng
 *
 * Sau này bạn cũng có thể mở rộng class này để gửi:
 * - email chào mừng đăng ký tài khoản
 * - email thông báo khóa tài khoản
 * - email xác nhận hệ thống
 */
@Service
public class EmailService {

    /**
     * JavaMailSender là component do Spring cung cấp
     * để gửi email thông qua SMTP.
     *
     * Nó sẽ dùng các cấu hình trong application.properties như:
     * - spring.mail.host
     * - spring.mail.port
     * - spring.mail.username
     * - spring.mail.password
     */
    private final JavaMailSender mailSender;

    /**
     * Email người gửi.
     *
     * Giá trị được lấy từ file cấu hình:
     * app.mail.from=your_email@gmail.com
     *
     * Email này sẽ hiển thị ở phần "From" khi người dùng nhận mail.
     */
    @Value("${app.mail.from}")
    private String fromEmail;

    /**
     * Constructor inject JavaMailSender.
     *
     * @param mailSender component gửi mail của Spring
     */
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Gửi email chứa mã OTP để đặt lại mật khẩu.
     *
     * Luồng hoạt động:
     * 1. Tạo đối tượng email đơn giản
     * 2. Gán email người gửi
     * 3. Gán email người nhận
     * 4. Gán tiêu đề mail
     * 5. Gán nội dung mail
     * 6. Gửi mail qua SMTP
     *
     * @param toEmail email người nhận
     * @param username username của người nhận để chèn vào nội dung mail
     * @param otpCode mã OTP gửi cho người dùng
     * @param expireMinutes số phút hiệu lực của OTP
     */
    public void sendPasswordResetOtp(String toEmail, String username, String otpCode, long expireMinutes) {
        // Tạo email dạng text đơn giản
        SimpleMailMessage message = new SimpleMailMessage();

        // Địa chỉ email người gửi
        message.setFrom(fromEmail);

        // Địa chỉ email người nhận
        message.setTo(toEmail);

        // Tiêu đề email
        message.setSubject("[Bomber Game] Mã xác thực đặt lại mật khẩu");

        // Nội dung email
        message.setText(buildResetOtpContent(username, otpCode, expireMinutes));

        // Gửi email
        mailSender.send(message);
    }

    /**
     * Tạo nội dung text cho email OTP reset password.
     *
     * Nội dung gồm:
     * - lời chào người dùng
     * - thông báo vừa yêu cầu đặt lại mật khẩu
     * - mã OTP
     * - thời gian hiệu lực
     * - cảnh báo nếu không phải người dùng yêu cầu thì bỏ qua
     *
     * @param username tên người dùng
     * @param otpCode mã OTP
     * @param expireMinutes số phút hiệu lực
     * @return chuỗi nội dung email hoàn chỉnh
     */
    private String buildResetOtpContent(String username, String otpCode, long expireMinutes) {
        return "Xin chào " + username + ",\n\n"
                + "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Bomber Game.\n"
                + "Mã OTP của bạn là: " + otpCode + "\n"
                + "Mã này có hiệu lực trong " + expireMinutes + " phút.\n\n"
                + "Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email này.\n\n"
                + "Bomber Game";
    }
}