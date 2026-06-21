package com.paymentapp.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.javax.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

import javax.servlet.Filter;

/**
 * Enables AWS X-Ray distributed tracing — the Observability tier in the
 * architecture diagram.
 *
 * <p>The {@link AWSXRayServletFilter} opens a trace segment for every inbound
 * HTTP request; combined with the {@code aws-xray-recorder-sdk-aws-sdk-v2}
 * instrumentor on the classpath, downstream calls to SNS / Secrets Manager /
 * CloudWatch surface as subsegments in the X-Ray service map.
 *
 * <p>Wired only under the {@code prod} profile so the test suite (which runs
 * with no active profile) never registers the filter, emits segments, or tries
 * to reach the X-Ray daemon. Segments are shipped to the daemon at the address
 * in {@code AWS_XRAY_DAEMON_ADDRESS} (default {@code 127.0.0.1:2000}).
 */
@Slf4j
@Configuration
@Profile("prod")
public class XRayConfig {

    @Value("${app.xray.segment-name:PaymentApp}")
    private String segmentName;

    /**
     * Registers the X-Ray recorder as soon as the config loads, so the global
     * recorder is initialised before the first request hits the servlet filter.
     */
    public XRayConfig() {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard()
                .withSamplingStrategy(new LocalizedSamplingStrategy());
        AWSXRay.setGlobalRecorder(builder.build());
        log.info("AWS X-Ray global recorder initialised");
    }

    /**
     * The tracing filter runs ahead of everything else (including the rate-limit
     * and security filters) so the whole request lifecycle is captured in one
     * segment.
     */
    @Bean
    public FilterRegistrationBean<Filter> xrayTracingFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AWSXRayServletFilter(segmentName));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("awsXRayServletFilter");
        return registration;
    }
}