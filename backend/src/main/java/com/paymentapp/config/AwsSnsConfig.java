package com.paymentapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Provides the AWS SNS client used to deliver OTP SMS in production.
 *
 * <p>Credentials are resolved by {@link DefaultCredentialsProvider} (IAM
 * role / instance profile / env), so no access keys live in the codebase.
 * Only created under the {@code prod} profile.
 */
@Configuration
@Profile("prod")
public class AwsSnsConfig {

    @Value("${app.sms.aws.region:us-east-1}")
    private String region;

    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}