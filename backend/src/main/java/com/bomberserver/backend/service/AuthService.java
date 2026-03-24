package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.PasswordResetOtpDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.auth.AuthResponse;
import com.bomberserver.backend.dto.auth.ForgotPasswordRequest;
import com.bomberserver.backend.dto.auth.LoginRequest;
import com.bomberserver.backend.dto.auth.RegisterRequest;
import com.bomberserver.backend.dto.auth.ResetPasswordRequest;
import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.PasswordResetOtpRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import com.bomberserver.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${app.auth.reset-otp-expire-minutes:10}")
    private long resetOtpExpireMinutes;

    @Value("${app.auth.reset-otp-resend-seconds:60}")
    private long resetOtpResendSeconds;

    public AuthService(
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository,
            PasswordResetOtpRepository passwordResetOtpRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.passwordResetOtpRepository = passwordResetOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email.trim().toLowerCase();
        String username = request.username.trim();

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }

        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }

        UserAccountDocument user = new UserAccountDocument(
                email,
                username,
                passwordEncoder.encode(request.password)
        );

        user.setRole("USER");
        user.setActive(true);

        user = userAccountRepository.save(user);

        CharacterProfileDocument profile = new CharacterProfileDocument(user.getId());
        profile = characterProfileRepository.save(profile);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                false,
                user.getRole()
        );
    }

    public AuthResponse login(LoginRequest request) {
        String login = request.login.trim();

        UserAccountDocument user = userAccountRepository.findByEmailIgnoreCase(login)
                .or(() -> userAccountRepository.findByUsernameIgnoreCase(login))
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("Tài khoản đã bị khóa");
        }

        if (!passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Sai mật khẩu");
        }

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(user.getId())));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                profile.isProfileCompleted(),
                user.getRole()
        );
    }

    public MessageResponse requestForgotPassword(ForgotPasswordRequest request) {
        String email = request.email.trim().toLowerCase();

        Optional<UserAccountDocument> userOpt = userAccountRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
        }

        UserAccountDocument user = userOpt.get();

        if (Boolean.FALSE.equals(user.getActive())) {
            return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
        }

        Optional<PasswordResetOtpDocument> latestOtpOpt =
                passwordResetOtpRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);

        if (latestOtpOpt.isPresent()) {
            Instant lastCreatedAt = latestOtpOpt.get().getCreatedAt();
            if (lastCreatedAt != null && lastCreatedAt.plusSeconds(resetOtpResendSeconds).isAfter(Instant.now())) {
                throw new IllegalArgumentException(
                        "Bạn vừa yêu cầu mã xác thực. Vui lòng đợi "
                                + resetOtpResendSeconds
                                + " giây rồi thử lại."
                );
            }
        }

        invalidateActiveOtps(email);

        String otpCode = generateOtpCode();
        PasswordResetOtpDocument otpDocument = new PasswordResetOtpDocument(
                user.getId(),
                email,
                passwordEncoder.encode(otpCode),
                Instant.now().plusSeconds(resetOtpExpireMinutes * 60)
        );

        otpDocument = passwordResetOtpRepository.save(otpDocument);

        try {
            emailService.sendPasswordResetOtp(email, user.getUsername(), otpCode, resetOtpExpireMinutes);
        } catch (MailException ex) {
            passwordResetOtpRepository.deleteById(otpDocument.getId());
            throw new IllegalArgumentException("Không thể gửi email xác thực. Vui lòng kiểm tra cấu hình SMTP.");
        }

        return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = request.email.trim().toLowerCase();
        String otpCode = request.otpCode.trim();
        String newPassword = request.newPassword;

        UserAccountDocument user = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn"));

        PasswordResetOtpDocument otpDocument = passwordResetOtpRepository
                .findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn"));

        if (otpDocument.getExpiresAt() == null || otpDocument.getExpiresAt().isBefore(Instant.now())) {
            otpDocument.setUsed(true);
            otpDocument.setUsedAt(Instant.now());
            passwordResetOtpRepository.save(otpDocument);
            throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        if (!passwordEncoder.matches(otpCode, otpDocument.getOtpCodeHash())) {
            throw new IllegalArgumentException("Mã OTP không đúng");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng mật khẩu cũ");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);

        otpDocument.setUsed(true);
        otpDocument.setUsedAt(Instant.now());
        passwordResetOtpRepository.save(otpDocument);

        invalidateActiveOtps(email);

        return new MessageResponse("Đặt lại mật khẩu thành công. Bạn hãy đăng nhập lại.");
    }

    private void invalidateActiveOtps(String email) {
        List<PasswordResetOtpDocument> activeOtps = passwordResetOtpRepository.findByEmailIgnoreCaseAndUsedFalse(email);
        Instant now = Instant.now();

        for (PasswordResetOtpDocument otp : activeOtps) {
            otp.setUsed(true);
            otp.setUsedAt(now);
        }

        if (!activeOtps.isEmpty()) {
            passwordResetOtpRepository.saveAll(activeOtps);
        }
    }

    private String generateOtpCode() {
        int number = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(number);
    }
}