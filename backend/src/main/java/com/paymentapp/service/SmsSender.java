package com.paymentapp.service;

/**
 * Abstraction for delivering an OTP to a user's mobile number. Swap the
 * implementation (e.g. Twilio, AWS SNS) without touching the payment flow.
 */
public interface SmsSender {
    void sendOtp(String mobileNumber, String otp);
}

