package com.codepromptu.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component("redis")
public class CustomRedisHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(CustomRedisHealthIndicator.class);

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @PostConstruct
    public void init() {
        logger.info("=== API CUSTOM REDIS HEALTH INDICATOR INITIALIZED ===");
        logger.info("Redis Connection Factory: {}", redisConnectionFactory.getClass().getSimpleName());
    }

    @Override
    public Health health() {
        logger.info("=== API CUSTOM REDIS HEALTH CHECK CALLED ===");
        try {
            // Use our configured Redis connection factory
            var connection = redisConnectionFactory.getConnection();
            
            // Perform a simple ping operation
            String pong = connection.ping();
            connection.close();
            
            logger.info("API Redis health check SUCCESS: {}", pong);
            return Health.up()
                .withDetail("ping", pong != null ? "PONG" : "No response")
                .withDetail("status", "Connected successfully using Config Server properties")
                .withDetail("connectionFactory", redisConnectionFactory.getClass().getSimpleName())
                .withDetail("service", "api")
                .build();
                
        } catch (Exception e) {
            logger.error("API Redis health check FAILED: {}", e.getMessage());
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Connection failed")
                .withDetail("connectionFactory", redisConnectionFactory.getClass().getSimpleName())
                .withDetail("service", "api")
                .build();
        }
    }
}
