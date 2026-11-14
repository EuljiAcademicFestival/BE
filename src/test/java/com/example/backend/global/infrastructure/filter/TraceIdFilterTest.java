package com.example.backend.global.infrastructure.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class TraceIdFilterTest {
    @Test
    void setsRequestContextAndRestoresPreviousContextAfterCompletion() throws Exception {
        MDC.put("existing", "parent");
        var request = new MockHttpServletRequest("GET", "/api/guestbook");
        var response = new MockHttpServletResponse();
        try {
            new TraceIdFilter().doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("traceId")).matches("[a-f0-9]{32}");
                assertThat(MDC.get("method")).isEqualTo("GET");
                assertThat(MDC.get("requestURI")).isEqualTo("/api/guestbook");
            });
            assertThat(response.getHeader("X-Trace-Id")).matches("[a-f0-9]{32}");
            assertThat(MDC.get("traceId")).isNull();
            assertThat(MDC.get("existing")).isEqualTo("parent");
        } finally { MDC.clear(); }
    }

    @Test
    void cleansContextEvenWhenTheRequestThrows() {
        MDC.clear();
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        assertThatThrownBy(() -> new TraceIdFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("test failure"); }))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("requestURI")).isNull();
    }

    @Test
    void doesNotTraceMonitoringRequests() throws Exception {
        var response = new MockHttpServletResponse();
        new TraceIdFilter().doFilter(new MockHttpServletRequest("GET", "/actuator/prometheus"), response,
                (req, res) -> assertThat(MDC.get("traceId")).isNull());
        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }
}
