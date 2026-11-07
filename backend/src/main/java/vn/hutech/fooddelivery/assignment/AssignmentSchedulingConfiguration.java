package vn.hutech.fooddelivery.assignment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Opt-in de Scheduler khong sua fixture cua cac SpringBootTest/API khi chua duoc bat ro rang. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.assignment.scheduling", name = "enabled", havingValue = "true")
@EnableScheduling
public class AssignmentSchedulingConfiguration {
    @Bean
    AssignmentScheduler assignmentScheduler(AssignmentRecoveryService recovery) {
        return new AssignmentScheduler(recovery);
    }
}
