package com.game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Application-level beans:
 * <ul>
 *   <li>Jackson {@link ObjectMapper} configured for Java-time and non-null output.</li>
 *   <li>{@link ThreadPoolTaskScheduler} for the cleanup service (Spring {@code @Scheduled}).</li>
 *   <li>Enables Spring scheduling via {@link EnableScheduling}.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class AppConfig {

    /**
     * Shared {@link ObjectMapper} used by the WebSocket handler and REST controllers.
     * Configured once and injected everywhere to avoid per-request instantiation cost.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Dedicated scheduler thread pool for {@code @Scheduled} tasks (cleanup, etc.).
     * Using a named pool makes it easy to tune and monitor.
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("game-scheduler-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}

