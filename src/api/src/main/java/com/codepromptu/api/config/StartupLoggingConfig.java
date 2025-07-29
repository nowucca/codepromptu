package com.codepromptu.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLoggingConfig implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StartupLoggingConfig.class);

    @Autowired
    private Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("=== API SERVICE STARTUP CONFIGURATION ===");
        
        // Log active profiles
        String[] activeProfiles = environment.getActiveProfiles();
        logger.info("Active Profiles: {}", String.join(", ", activeProfiles));
        
        // Log Spring Cloud Config properties
        logger.info("Spring Application Name: {}", environment.getProperty("spring.application.name"));
        logger.info("Config Server URI: {}", environment.getProperty("spring.cloud.config.uri"));
        logger.info("Config Server Enabled: {}", environment.getProperty("spring.cloud.config.enabled"));
        logger.info("Config Import: {}", environment.getProperty("spring.config.import"));
        
        // Log Redis configuration
        logger.info("Redis Host: {}", environment.getProperty("spring.redis.host"));
        logger.info("Redis Port: {}", environment.getProperty("spring.redis.port"));
        logger.info("Redis Timeout: {}", environment.getProperty("spring.redis.timeout"));
        
        // Log Database configuration
        logger.info("Database URL: {}", environment.getProperty("spring.datasource.url"));
        logger.info("Database Driver: {}", environment.getProperty("spring.datasource.driver-class-name"));
        
        // Log property sources
        logger.info("=== PROPERTY SOURCES ===");
        event.getApplicationContext().getEnvironment().getPropertySources().forEach(propertySource -> {
            logger.info("Property Source: {} ({})", propertySource.getName(), propertySource.getClass().getSimpleName());
        });
        
        logger.info("=== END STARTUP CONFIGURATION ===");
    }
}
