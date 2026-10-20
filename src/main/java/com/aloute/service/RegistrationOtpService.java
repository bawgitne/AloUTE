package com.aloute.service;

import com.aloute.entity.User;

public interface RegistrationOtpService {
    void initiateRegistrationOtp(User user);
    void verifyOtp(String email, String otp);
    void resendOtp(String email);
}
