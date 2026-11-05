package vn.hutech.fooddelivery.assignment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssignmentSchedulingTest {
    private static final AssignmentRecoveryService.Report EMPTY = new AssignmentRecoveryService.Report(0, 0, 0, 0, 0, 0, 0, 0);

    @Test
    void defaultAndExplicitlyDisabledConfigurationDoNotStartScheduler() {
        for (String value : new String[]{"", "app.assignment.scheduling.enabled=false"}) {
            var recovery = mock(AssignmentRecoveryService.class);
            var context = new ApplicationContextRunner().withUserConfiguration(AssignmentSchedulingConfiguration.class)
                    .withBean(AssignmentRecoveryService.class, () -> recovery);
            if (!value.isEmpty()) context = context.withPropertyValues(value);
            context.run(application -> {
                assertFalse(application.containsBean("assignmentScheduler"));
                assertEquals(0, application.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).size());
                verifyNoInteractions(recovery);
            });
        }
    }

    @Test
    void enabledConfigurationRegistersTwoSchedulesAndActuallyRunsBoth() {
        var recovery = mock(AssignmentRecoveryService.class);
        var expiryCalled = new CountDownLatch(1);
        var recoveryCalled = new CountDownLatch(1);
        when(recovery.expireAndReassign()).thenAnswer(call -> { expiryCalled.countDown(); return EMPTY; });
        when(recovery.recoverWaitingOrders()).thenAnswer(call -> { recoveryCalled.countDown(); return EMPTY; });
        new ApplicationContextRunner().withUserConfiguration(AssignmentSchedulingConfiguration.class)
                .withBean(AssignmentRecoveryService.class, () -> recovery)
                .withPropertyValues("app.assignment.scheduling.enabled=true",
                        "app.assignment.scheduling.expire-delay-ms=20", "app.assignment.scheduling.recovery-delay-ms=30",
                        "app.assignment.scheduling.expire-initial-delay-ms=0", "app.assignment.scheduling.recovery-initial-delay-ms=0")
                .run(application -> {
                    assertTrue(application.containsBean("assignmentScheduler"));
                    assertEquals(2, application.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().size());
                    assertTrue(expiryCalled.await(5, TimeUnit.SECONDS));
                    assertTrue(recoveryCalled.await(5, TimeUnit.SECONDS));
                });
    }

    @Test
    void idleTicksAreSilentAndErrorsKeepStackTraceInsteadOfClaimingSuccess() {
        var logger = (Logger) LoggerFactory.getLogger(AssignmentScheduler.class);
        var levelBefore = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            var recovery = mock(AssignmentRecoveryService.class);
            when(recovery.expireAndReassign()).thenReturn(EMPTY);
            var scheduler = new AssignmentScheduler(recovery);
            scheduler.expireOffers();
            assertTrue(appender.list.isEmpty());

            when(recovery.expireAndReassign()).thenThrow(new DataAccessResourceFailureException("Test database unavailable"));
            scheduler.expireOffers();
            assertEquals(1, appender.list.size());
            assertEquals(Level.ERROR, appender.list.getFirst().getLevel());
            assertNotNull(appender.list.getFirst().getThrowableProxy());

            appender.list.clear();
            when(recovery.recoverWaitingOrders()).thenReturn(new AssignmentRecoveryService.Report(3, 1, 0, 2, 1, 1, 0, 1));
            scheduler.recoverOrders();
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("created=1"));
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("errors=1"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(levelBefore);
            appender.stop();
        }
    }
}
