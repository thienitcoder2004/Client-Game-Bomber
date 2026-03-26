package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.PasswordResetOtpDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection password_reset_otps.
 *
 * Dùng trong chức năng quên mật khẩu:
 * - lưu OTP
 * - tìm OTP mới nhất
 * - kiểm tra OTP chưa dùng
 * - vô hiệu hóa / đánh dấu đã dùng
 */
public interface PasswordResetOtpRepository extends MongoRepository<PasswordResetOtpDocument, String> {

    /**
     * Lấy OTP mới nhất theo email, không phân biệt hoa thường.
     *
     * Dùng khi cần lấy bản ghi OTP gần nhất bất kể đã dùng hay chưa.
     *
     * @param email email cần tìm
     * @return Optional chứa OTP mới nhất
     */
    Optional<PasswordResetOtpDocument> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    /**
     * Lấy OTP mới nhất theo email và chưa được sử dụng.
     *
     * Dùng khi xác thực reset password.
     *
     * @param email email cần tìm
     * @return Optional chứa OTP mới nhất chưa dùng
     */
    Optional<PasswordResetOtpDocument> findTopByEmailIgnoreCaseAndUsedFalseOrderByCreatedAtDesc(String email);

    /**
     * Lấy tất cả OTP chưa dùng theo email.
     *
     * Thường dùng để đánh dấu hết hiệu lực các OTP cũ
     * trước khi tạo OTP mới.
     *
     * @param email email cần tìm
     * @return danh sách OTP chưa dùng
     */
    List<PasswordResetOtpDocument> findByEmailIgnoreCaseAndUsedFalse(String email);
}