package com.example.backend.global.infrastructure.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceLogAspect {
    private static final Logger log = LoggerFactory.getLogger(PerformanceLogAspect.class);

    @Around("execution(public * com.example.backend..application.service..*(..))")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            log.info("event=performance method={} durationMs={} outcome={}",
                    joinPoint.getSignature().toShortString(), (System.nanoTime() - started) / 1_000_000,
                    succeeded ? "success" : "failure");
        }
    }
}
