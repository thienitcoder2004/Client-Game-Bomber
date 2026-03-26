package com.bomberserver.backend.dto.common;

/**
 * DTO phản hồi đơn giản chỉ chứa 1 message.
 *
 * Dùng cho các API chỉ cần trả thông báo như:
 * - gửi OTP thành công
 * - đổi mật khẩu thành công
 * - xóa user thành công
 * - kết bạn thành công
 */
public class MessageResponse {

    /**
     * Nội dung thông báo trả về cho frontend.
     */
    public String message;

    /**
     * Constructor rỗng.
     */
    public MessageResponse() {
    }

    /**
     * Constructor tạo nhanh object chứa message.
     *
     * @param message nội dung thông báo
     */
    public MessageResponse(String message) {
        this.message = message;
    }
}