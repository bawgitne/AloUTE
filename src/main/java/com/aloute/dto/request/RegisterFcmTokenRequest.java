package com.aloute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFcmTokenRequest {

    @NotBlank(message = "Token must not be blank")
    private String token;
}
