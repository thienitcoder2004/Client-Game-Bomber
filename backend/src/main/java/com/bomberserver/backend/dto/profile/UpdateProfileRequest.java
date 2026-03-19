package com.bomberserver.backend.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @NotBlank(message = "Tên nhân vật không được để trống")
    @Size(min = 2, max = 20, message = "Tên nhân vật phải từ 2 đến 20 ký tự")
    public String characterName;

    @NotBlank(message = "Giới tính không được để trống")
    @Pattern(regexp = "MALE|FEMALE", message = "Giới tính phải là MALE hoặc FEMALE")
    public String gender;

    @NotBlank(message = "Avatar không được để trống")
    public String avatarCode;
}