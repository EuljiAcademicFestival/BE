package com.example.backend.global.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/")
                && !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        long started = System.nanoTime();
        boolean failed = false;
        try {
            MDC.put("traceId", traceId);
            MDC.put("method", request.getMethod());
            MDC.put("requestURI", singleLine(request.getRequestURI()));
            MDC.put("remoteAddr", singleLine(request.getRemoteAddr()));
            response.setHeader("X-Trace-Id", traceId);
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failed = true;
            throw ex;
        } finally {
            try {
                log.info("event=http_request status={} durationMs={}", failed ? 500 : response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000);
            } finally {
                if (previousContext == null) MDC.clear();
                else MDC.setContextMap(previousContext);
            }
        }
    }

    private String singleLine(String value) {
        return value == null ? "" : value.replace('\r', '_').replace('\n', '_');
    }
}
