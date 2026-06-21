package com.paymentapp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimitFilter Unit Tests")
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "capacity", 2L);
        ReflectionTestUtils.setField(filter, "refillPeriodSeconds", 60L);
    }

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(ip);
        req.setRequestURI("/api/payment");
        return req;
    }

    @Test
    @DisplayName("requests within the capacity pass through")
    void withinLimit_passesThrough() throws Exception {
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request("10.0.0.1"), resp, chain);
            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(chain.getRequest()).isNotNull(); // chain was invoked
        }
    }

    @Test
    @DisplayName("requests beyond the capacity get HTTP 429")
    void overLimit_returns429() throws Exception {
        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("10.0.0.2"), new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("10.0.0.2"), resp, chain);

        assertThat(resp.getStatus()).isEqualTo(429);
        assertThat(resp.getContentAsString()).contains("Too Many Requests");
        assertThat(chain.getRequest()).isNull(); // request was NOT forwarded
    }

    @Test
    @DisplayName("buckets are tracked per client IP")
    void perClient_independentBuckets() throws Exception {
        // Exhaust client A
        for (int i = 0; i < 2; i++) {
            filter.doFilter(request("10.0.0.3"), new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("10.0.0.3"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);

        // A different client still has a full bucket
        MockHttpServletResponse other = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("10.0.0.4"), other, chain);
        assertThat(other.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("disabled filter never throttles")
    void disabled_alwaysPasses() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request("10.0.0.5"), resp, new MockFilterChain());
            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }
}