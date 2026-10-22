package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeactivateAccountRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu xác nhận")
        String password
) {
}
