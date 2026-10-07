package com.aryan.spring_security_demo.common.aop;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingAspectTest {

    private final LoggingAspect aspect = new LoggingAspect();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Level previousLevel;

    /** Stands in for the advised bean; only its name shows up in the log line. */
    static class ProductService {
    }

    @BeforeEach
    void captureLogs() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
        logger.setLevel(previousLevel);
    }

    // Regression: a client mistake such as a missing product (404) was logged at
    // ERROR twice per request: here, and again at the controller boundary.
    @Test
    void aFailingServiceCall_isLoggedAtDebugAndRethrown() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("getProductById");
        ResourceNotFoundException notFound = new ResourceNotFoundException("product not found");
        when(joinPoint.proceed()).thenThrow(notFound);

        assertThatThrownBy(() -> aspect.logAround(joinPoint)).isSameAs(notFound);

        assertThat(events.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
        assertThat(events.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage()).contains("ProductService.getProductById failed after");
        });
    }

    @Test
    void anExceptionLeavingAController_isLoggedAtDebug() {
        aspect.logAfterThrowingControllerMethod(joinPoint("getProductById"),
                new ResourceNotFoundException("product not found"));

        assertThat(events.list).extracting(ILoggingEvent::getLevel).containsOnly(Level.DEBUG);
    }

    private static ProceedingJoinPoint joinPoint(String method) {
        Signature signature = mock(Signature.class);
        doReturn(ProductService.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }
}
