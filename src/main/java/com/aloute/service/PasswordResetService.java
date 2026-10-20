package com.aloute.service;

import com.aloute.dto.request.ForgotPasswordRequest;
import com.aloute.dto.request.ResetPasswordRequest;

public interface PasswordResetService {
    void initiateForgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
