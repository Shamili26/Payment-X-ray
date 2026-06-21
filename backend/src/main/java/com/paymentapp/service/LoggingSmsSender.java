package com.paymentapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local/dev OTP delivery. Active for every profile EXCEPT {@code prod}, where
 * {@link SnsSmsSender} sends a real SMS via AWS SNS.
 *
 * <p>The OTP value is intentionally NOT logged — logging one-time secrets would
 * leak credentials into log aggregation. Only the masked destination is logged.
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingSmsSender implements SmsSender {

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        log.info("[DEV-SMS] OTP generated for mobile {} (value suppressed; configure AWS SNS via the 'prod' profile to deliver real SMS)",
                mask(mobileNumber));
    }

    private String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "●●●●●●" + mobile.substring(mobile.length() - 4);
    }
}