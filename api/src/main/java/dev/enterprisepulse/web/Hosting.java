package dev.enterprisepulse.web;

import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.platform.Extensibility.ScheduledJob;
import dev.enterprisepulse.platform.Messaging;
import dev.enterprisepulse.seed.DataSeeder;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

public final class Hosting {
    private Hosting() {}

    /** STOMP over WebSocket at /ws (JWT in the access_token query parameter); per-user queue /user/queue/notifications. */
    @Configuration
    @EnableWebSocketMessageBroker
    public static class Realtime implements WebSocketMessageBrokerConfigurer {
        private final AppProperties props;

        public Realtime(AppProperties props) {
            this.props = props;
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            registry.enableSimpleBroker("/queue");
            registry.setUserDestinationPrefix("/user");
        }
    }

    @Component
    public static class StompPusher implements Messaging.Pusher {
        private final SimpMessagingTemplate template;

        public StompPusher(SimpMessagingTemplate template) {
            this.template = template;
        }

        @Override
        public void push(Collection<UUID> userIds, String event, Object payload) {
            for (UUID id : userIds) template.convertAndSendToUser(id.toString(), "/queue/notifications", Map.of("event", event, "payload", payload));
        }
    }

    /** Applies migrations (Flyway, automatic), seeds demo data and registers the recurring jobs. */
    @Component
    public static class Startup implements ApplicationRunner {
        private static final Logger log = LoggerFactory.getLogger(Startup.class);
        private final AppProperties props;
        private final DataSeeder seeder;
        private final List<ScheduledJob> jobs;
        private final TaskScheduler scheduler;
        private final LockProvider locks;

        public Startup(AppProperties props, DataSeeder seeder, List<ScheduledJob> jobs, TaskScheduler scheduler, LockProvider locks) {
            this.props = props;
            this.seeder = seeder;
            this.jobs = jobs;
            this.scheduler = scheduler;
            this.locks = locks;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (props.seedOnStartup) seeder.seed();
            var executor = new DefaultLockingTaskExecutor(locks);
            for (ScheduledJob job : jobs) {
                scheduler.schedule(() -> {
                    try {
                        executor.executeWithLock((Runnable) () -> runJob(job), new LockConfiguration(Instant.now(), job.id(), Duration.ofMinutes(30), Duration.ofSeconds(5)));
                    } catch (RuntimeException e) {
                        log.error("Job {} failed", job.id(), e);
                    }
                }, new CronTrigger(job.cron(), ZoneId.of("Europe/Istanbul")));
                log.info("Scheduled job {} ({})", job.id(), job.cron());
            }
        }

        private static void runJob(ScheduledJob job) {
            CurrentUser.reset();
            try {
                job.run();
            } finally {
                CurrentUser.reset();
            }
        }
    }
}
