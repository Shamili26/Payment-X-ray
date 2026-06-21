package com.paymentapp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Production OTP delivery via AWS SNS. Active only under the {@code prod}
 * profile (see {@link AwsSnsConfig} for the {@link SnsClient} bean).
 *
 * <p>Sends the OTP as a transactional SMS. The OTP value is never logged.
 */
@Slf4j
@Component
@Profile("prod")
public class SnsSmsSender implements SmsSender {

    private final SnsClient snsClient;

    @Value("${app.sms.aws.sender-id:}")
    private String senderId;

    @Value("${app.sms.aws.sms-type:Transactional}")
    private String smsType;

    public SnsSmsSender(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put("AWS.SNS.SMS.SMSType",
                MessageAttributeValue.builder().dataType("String").stringValue(smsType).build());
        if (senderId != null && !senderId.isBlank()) {
            attributes.put("AWS.SNS.SMS.SenderID",
                    MessageAttributeValue.builder().dataType("String").stringValue(senderId).build());
        }

        PublishRequest request = PublishRequest.builder()
                .phoneNumber(mobileNumber)
                .message("Your PayFlow verification code is " + otp + ". It expires in 5 minutes. Do not share it with anyone.")
                .messageAttributes(attributes)
                .build();

        PublishResponse response = snsClient.publish(request);
        log.info("OTP SMS dispatched via SNS to {} (messageId={})", mask(mobileNumber), response.messageId());
    }

    private String mask(String mobile) {
        if (mobile == null || mobile.length() < 4) return "****";
        return "●●●●●●" + mobile.substring(mobile.length() - 4);
    }
}