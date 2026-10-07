package com.rockey.hospitality.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rockey.hospitality.configuration.ApplicationConfiguration;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AlertSchedulerTest {

    @Test
    void schedulerUsesOneConfigurableFixedDelayRatherThanFixedRateOrCron() throws Exception {
        Scheduled scheduled = AlertScheduler.class.getMethod("scan").getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString()).isEqualTo("${rockey.alerts.scan-delay-ms:300000}");
        assertThat(scheduled.initialDelayString()).isEqualTo(scheduled.fixedDelayString());
        assertThat(scheduled.fixedRateString()).isEmpty();
        assertThat(scheduled.cron()).isEmpty();
        assertThat(Arrays.stream(AlertScheduler.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class)).count()).isEqualTo(1);
        assertThat(ApplicationConfiguration.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }

    @Test
    void defaultCadenceIsFiveMinutesAndExternallyConfigurable() throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(input);
        }
        assertThat(properties.getProperty("rockey.alerts.scan-delay-ms"))
                .isEqualTo("${ROCKEY_ALERT_SCAN_DELAY_MS:300000}");
    }

    @Test
    void directSchedulerInvocationDelegatesWithoutWaiting() {
        AlertAutomationService service = mock(AlertAutomationService.class);
        new AlertScheduler(service).scan();
        verify(service).runChecks();
    }

    @Test
    void failedScanAllowsLaterAttemptAndDoesNotExposeExceptionMessage() {
        AlertAutomationService service = mock(AlertAutomationService.class);
        doThrow(new IllegalStateException("Simulated private failure details")).doNothing().when(service).runChecks();
        AlertScheduler scheduler = new AlertScheduler(service);
        Logger logger = (Logger) LoggerFactory.getLogger(AlertScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(scheduler::scan).doesNotThrowAnyException();
            scheduler.scan();
            verify(service, times(2)).runChecks();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Alert automation scan failed: IllegalStateException");
            assertThat(appender.list).allMatch(event -> event.getThrowableProxy() == null);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
