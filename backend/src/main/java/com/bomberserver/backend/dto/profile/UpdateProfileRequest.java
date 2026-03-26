package com.bomberserver.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO nhận dữ liệu cập nhật hồ sơ nhân vật từ frontend.
 *
 * Người dùng sẽ gửi:
 * - tên nhân vật
 * - giới tính
 * - avatarCode
 */
public class UpdateProfileRequest {

    /**
     * Tên nhân vật mới.
     *
     * Yêu cầu:
     * - không để trống
     * - từ 2 đến 20 ký tự
     */
    @NotBlank(message = "Tên nhân vật không được để trống")
    @Size(min = 2, max = 20, message = "Tên nhân vật phải từ 2 đến 20 ký tự")
    public String characterName;

    /**
     * Giới tính nhân vật.
     *
     * Chỉ cho phép 2 giá trị:
     * - MALE
     * - FEMALE
     */
    @NotBlank(message = "Giới tính không được để trống")
    @Pattern(regexp = "MALE|FEMALE", message = "Giới tính phải là MALE hoặc FEMALE")
    public String gender;

    /**
     * Mã avatar được chọn.
     *
     * Không được để trống.
     */
    @NotBlank(message = "Avatar không được để trống")
    public String avatarCode;
}