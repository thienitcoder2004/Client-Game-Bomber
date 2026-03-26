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

/**
 * Service xử lý toàn bộ nghiệp vụ xác thực tài khoản.
 *
 * Chức năng chính:
 * - Đăng ký tài khoản mới
 * - Đăng nhập
 * - Quên mật khẩu và gửi OTP qua email
 * - Đặt lại mật khẩu bằng OTP
 *
 * Lớp này làm việc với:
 * - UserAccountRepository: quản lý dữ liệu tài khoản
 * - CharacterProfileRepository: quản lý hồ sơ nhân vật
 * - PasswordResetOtpRepository: quản lý mã OTP đặt lại mật khẩu
 * - PasswordEncoder: mã hóa và so khớp mật khẩu/OTP
 * - JwtService: tạo JWT token cho người dùng sau khi đăng nhập/đăng ký
 * - EmailService: gửi email chứa OTP
 */
@Service
public class AuthService {

    /**
     * Repository thao tác với collection tài khoản người dùng.
     */
    private final UserAccountRepository userAccountRepository;

    /**
     * Repository thao tác với collection hồ sơ nhân vật.
     */
    private final CharacterProfileRepository characterProfileRepository;

    /**
     * Repository thao tác với collection OTP đặt lại mật khẩu.
     */
    private final PasswordResetOtpRepository passwordResetOtpRepository;

    /**
     * Dùng để mã hóa mật khẩu và kiểm tra mật khẩu/OTP.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Service dùng để tạo JWT token sau khi đăng nhập/đăng ký thành công.
     */
    private final JwtService jwtService;

    /**
     * Service gửi email OTP cho người dùng.
     */
    private final EmailService emailService;

    /**
     * Thời gian hết hạn của OTP (tính theo phút).
     * Đọc từ application.properties:
     * app.auth.reset-otp-expire-minutes
     * Nếu không có thì mặc định là 10 phút.
     */
    @Value("${app.auth.reset-otp-expire-minutes:10}")
    private long resetOtpExpireMinutes;

    /**
     * Thời gian tối thiểu giữa 2 lần yêu cầu gửi lại OTP (tính theo giây).
     * Đọc từ application.properties:
     * app.auth.reset-otp-resend-seconds
     * Nếu không có thì mặc định là 60 giây.
     */
    @Value("${app.auth.reset-otp-resend-seconds:60}")
    private long resetOtpResendSeconds;

    /**
     * Constructor injection các dependency cần thiết cho AuthService.
     *
     * @param userAccountRepository repository tài khoản
     * @param characterProfileRepository repository hồ sơ nhân vật
     * @param passwordResetOtpRepository repository OTP reset password
     * @param passwordEncoder bộ mã hóa mật khẩu
     * @param jwtService service tạo JWT
     * @param emailService service gửi email
     */
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

    /**
     * Đăng ký tài khoản mới.
     *
     * Luồng xử lý:
     * 1. Chuẩn hóa email và username
     * 2. Kiểm tra email đã tồn tại chưa
     * 3. Kiểm tra username đã tồn tại chưa
     * 4. Tạo tài khoản mới, mã hóa mật khẩu trước khi lưu
     * 5. Gán role mặc định là USER và active = true
     * 6. Lưu tài khoản vào database
     * 7. Tạo profile nhân vật mặc định cho user
     * 8. Sinh JWT token
     * 9. Trả về AuthResponse cho frontend
     *
     * @param request dữ liệu đăng ký gồm email, username, password
     * @return thông tin xác thực sau khi đăng ký thành công
     */
    public AuthResponse register(RegisterRequest request) {
        // Chuẩn hóa email: bỏ khoảng trắng đầu/cuối và chuyển về chữ thường
        String email = request.email.trim().toLowerCase();

        // Chuẩn hóa username: bỏ khoảng trắng đầu/cuối
        String username = request.username.trim();

        // Kiểm tra email đã tồn tại hay chưa
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }

        // Kiểm tra username đã tồn tại hay chưa
        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }

        // Tạo user mới với mật khẩu đã được mã hóa
        UserAccountDocument user = new UserAccountDocument(
                email,
                username,
                passwordEncoder.encode(request.password)
        );

        // Gán quyền mặc định cho user là USER
        user.setRole("USER");

        // Tài khoản mới tạo được kích hoạt sẵn
        user.setActive(true);

        // Lưu user vào database
        user = userAccountRepository.save(user);

        // Tạo profile mặc định cho người dùng mới
        CharacterProfileDocument profile = new CharacterProfileDocument(user.getId());
        profile = characterProfileRepository.save(profile);

        // Tạo JWT token để frontend dùng cho các request tiếp theo
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        // Trả về dữ liệu xác thực cho frontend
        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                false, // user mới tạo thì profile mặc định chưa hoàn thiện
                user.getRole()
        );
    }

    /**
     * Đăng nhập bằng email hoặc username.
     *
     * Luồng xử lý:
     * 1. Nhận giá trị login từ request
     * 2. Tìm user theo email, nếu không có thì tìm theo username
     * 3. Kiểm tra tài khoản có bị khóa không
     * 4. Kiểm tra mật khẩu có đúng không
     * 5. Lấy profile nhân vật, nếu chưa có thì tạo mới
     * 6. Sinh JWT token
     * 7. Trả về AuthResponse
     *
     * @param request dữ liệu đăng nhập gồm login và password
     * @return thông tin xác thực sau khi đăng nhập thành công
     */
    public AuthResponse login(LoginRequest request) {
        // login có thể là email hoặc username
        String login = request.login.trim();

        // Tìm user theo email trước, nếu không có thì tìm theo username
        UserAccountDocument user = userAccountRepository.findByEmailIgnoreCase(login)
                .or(() -> userAccountRepository.findByUsernameIgnoreCase(login))
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        // Nếu tài khoản bị khóa thì không cho đăng nhập
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new IllegalArgumentException("Tài khoản đã bị khóa");
        }

        // Kiểm tra mật khẩu người dùng nhập có khớp với mật khẩu đã mã hóa trong DB không
        if (!passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Sai mật khẩu");
        }

        // Tìm profile nhân vật theo userId
        // Nếu chưa có profile thì tạo mới để tránh lỗi null
        CharacterProfileDocument profile = characterProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(user.getId())));

        // Tạo token JWT cho phiên đăng nhập hiện tại
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        // Trả về thông tin đăng nhập cho frontend
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

    /**
     * Xử lý yêu cầu quên mật khẩu.
     *
     * Ý tưởng bảo mật:
     * - Dù email có tồn tại hay không, hệ thống vẫn trả về cùng một message
     *   để tránh lộ thông tin email nào đang tồn tại trong hệ thống.
     *
     * Luồng xử lý:
     * 1. Chuẩn hóa email
     * 2. Tìm tài khoản theo email
     * 3. Nếu không tồn tại hoặc tài khoản bị khóa -> vẫn trả về message chung
     * 4. Kiểm tra thời gian gửi OTP gần nhất để chống spam
     * 5. Vô hiệu hóa các OTP cũ còn hiệu lực
     * 6. Tạo OTP mới
     * 7. Mã hóa OTP rồi lưu vào DB
     * 8. Gửi OTP qua email
     * 9. Nếu gửi mail lỗi thì xóa OTP vừa lưu
     *
     * @param request chứa email muốn đặt lại mật khẩu
     * @return thông báo chung cho frontend
     */
    public MessageResponse requestForgotPassword(ForgotPasswordRequest request) {
        // Chuẩn hóa email
        String email = request.email.trim().toLowerCase();

        // Tìm user theo email
        Optional<UserAccountDocument> userOpt = userAccountRepository.findByEmailIgnoreCase(email);

        // Không tìm thấy user -> vẫn trả về message chung để tránh lộ dữ liệu
        if (userOpt.isEmpty()) {
            return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
        }

        UserAccountDocument user = userOpt.get();

        // Nếu tài khoản bị khóa thì cũng không tiết lộ cho client biết
        if (Boolean.FALSE.equals(user.getActive())) {
            return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
        }

        // Lấy OTP gần nhất của email này để kiểm tra chống spam resend
        Optional<PasswordResetOtpDocument> latestOtpOpt =
                passwordResetOtpRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);

        if (latestOtpOpt.isPresent()) {
            Instant lastCreatedAt = latestOtpOpt.get().getCreatedAt();

            // Nếu lần gửi OTP trước vẫn chưa đủ thời gian chờ resend
            if (lastCreatedAt != null && lastCreatedAt.plusSeconds(resetOtpResendSeconds).isAfter(Instant.now())) {
                throw new IllegalArgumentException(
                        "Bạn vừa yêu cầu mã xác thực. Vui lòng đợi "
                                + resetOtpResendSeconds
                                + " giây rồi thử lại."
                );
            }
        }

        // Vô hiệu hóa toàn bộ OTP cũ còn active trước khi tạo OTP mới
        invalidateActiveOtps(email);

        // Tạo mã OTP 6 số
        String otpCode = generateOtpCode();

        // Tạo document OTP mới
        // Lưu hash của OTP thay vì lưu OTP thô để tăng bảo mật
        PasswordResetOtpDocument otpDocument = new PasswordResetOtpDocument(
                user.getId(),
                email,
                passwordEncoder.encode(otpCode),
                Instant.now().plusSeconds(resetOtpExpireMinutes * 60)
        );

        // Lưu OTP vào database
        otpDocument = passwordResetOtpRepository.save(otpDocument);

        try {
            // Gửi OTP qua email cho người dùng
            emailService.sendPasswordResetOtp(email, user.getUsername(), otpCode, resetOtpExpireMinutes);
        } catch (MailException ex) {
            // Nếu gửi mail lỗi, xóa OTP vừa tạo để tránh dữ liệu rác
            passwordResetOtpRepository.deleteById(otpDocument.getId());
            throw new IllegalArgumentException("Không thể gửi email xác thực. Vui lòng kiểm tra cấu hình SMTP.");
        }

        // Luôn trả về message chung
        return new MessageResponse("Nếu email tồn tại, mã xác thực đã được gửi.");
    }

    /**
     * Đặt lại mật khẩu bằng email + OTP + mật khẩu mới.
     *
     * Luồng xử lý:
     * 1. Chuẩn hóa email và otp
     * 2. Tìm user theo email
     * 3. Lấy OTP mới nhất còn chưa dùng
     * 4. Kiểm tra OTP có hết hạn chưa
     * 5. So khớp OTP người dùng nhập với hash trong DB
     * 6. Kiểm tra mật khẩu mới không được trùng mật khẩu cũ
     * 7. Cập nhật mật khẩu mới
     * 8. Đánh dấu OTP đã dùng
     * 9. Vô hiệu hóa toàn bộ OTP khác còn active của email đó
     *
     * @param request chứa email, otpCode, newPassword
     * @return message thành công
     */
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        // Chuẩn hóa dữ liệu đầu vào
        String email = request.email.trim().toLowerCase();
        String otpCode = request.otpCode.trim();
        String newPassword = request.newPassword;

        // Tìm user theo email
        // Nếu không có user thì trả lỗi chung để tránh lộ chi tiết quá nhiều
        UserAccountDocument user = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn"));

        // Lấy OTP mới nhất còn chưa sử dụng
        PasswordResetOtpDocument otpDocument = passwordResetOtpRepository
                .findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn"));

        // Kiểm tra OTP có hết hạn không
        if (otpDocument.getExpiresAt() == null || otpDocument.getExpiresAt().isBefore(Instant.now())) {
            // Nếu OTP hết hạn thì đánh dấu used để không bị dùng lại
            otpDocument.setUsed(true);
            otpDocument.setUsedAt(Instant.now());
            passwordResetOtpRepository.save(otpDocument);

            throw new IllegalArgumentException("Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        // So khớp OTP nhập vào với OTP hash đã lưu trong DB
        if (!passwordEncoder.matches(otpCode, otpDocument.getOtpCodeHash())) {
            throw new IllegalArgumentException("Mã OTP không đúng");
        }

        // Không cho phép đặt mật khẩu mới giống mật khẩu cũ
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng mật khẩu cũ");
        }

        // Mã hóa và cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);

        // Đánh dấu OTP hiện tại là đã sử dụng
        otpDocument.setUsed(true);
        otpDocument.setUsedAt(Instant.now());
        passwordResetOtpRepository.save(otpDocument);

        // Vô hiệu hóa tất cả OTP còn active khác của email này
        invalidateActiveOtps(email);

        // Trả thông báo thành công
        return new MessageResponse("Đặt lại mật khẩu thành công. Bạn hãy đăng nhập lại.");
    }

    /**
     * Vô hiệu hóa toàn bộ OTP còn active (used = false) của một email.
     *
     * Mục đích:
     * - Đảm bảo chỉ còn OTP mới nhất có thể được sử dụng
     * - Tránh việc người dùng dùng lại OTP cũ
     *
     * Cách làm:
     * 1. Lấy danh sách OTP chưa dùng của email
     * 2. Gán used = true cho tất cả
     * 3. Gán usedAt = thời điểm hiện tại
     * 4. Lưu lại toàn bộ danh sách
     *
     * @param email email cần vô hiệu hóa OTP
     */
    private void invalidateActiveOtps(String email) {
        // Lấy tất cả OTP của email này mà chưa được dùng
        List<PasswordResetOtpDocument> activeOtps = passwordResetOtpRepository.findByEmailIgnoreCaseAndUsedFalse(email);

        // Thời gian hiện tại để đánh dấu usedAt
        Instant now = Instant.now();

        // Duyệt toàn bộ OTP còn active và đánh dấu đã dùng
        for (PasswordResetOtpDocument otp : activeOtps) {
            otp.setUsed(true);
            otp.setUsedAt(now);
        }

        // Chỉ saveAll khi thực sự có dữ liệu để lưu
        if (!activeOtps.isEmpty()) {
            passwordResetOtpRepository.saveAll(activeOtps);
        }
    }

    /**
     * Sinh ngẫu nhiên mã OTP gồm 6 chữ số.
     *
     * nextInt(100000, 1000000) nghĩa là:
     * - Giá trị nhỏ nhất là 100000
     * - Giá trị lớn nhất là 999999
     *
     * Như vậy luôn đảm bảo OTP có đúng 6 chữ số.
     *
     * @return OTP dạng String
     */
    private String generateOtpCode() {
        int number = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(number);
    }
}