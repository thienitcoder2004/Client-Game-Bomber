package com.bomberserver.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetOtp(String toEmail, String username, String otpCode, long expireMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("[Bomber Game] Mã xác thực đặt lại mật khẩu");
        message.setText(buildResetOtpContent(username, otpCode, expireMinutes));
        mailSender.send(message);
    }

    private String buildResetOtpContent(String username, String otpCode, long expireMinutes) {
        return "Xin chào " + username + ",\n\n"
                + "Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Bomber Game.\n"
                + "Mã OTP của bạn là: " + otpCode + "\n"
                + "Mã này có hiệu lực trong " + expireMinutes + " phút.\n\n"
                + "Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email này.\n\n"
                + "Bomber Game";
    }
}