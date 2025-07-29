package com.codepromptu.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RedisConfigDebugger {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConfigDebugger.class);
    
    private final Environment environment;
    
    @Value("${spring.redis.host:NOT_SET}")
    private String redisHost;
    
    @Value("${spring.redis.port:NOT_SET}")
    private String redisPort;
    
    @Value("${spring.redis.timeout:NOT_SET}")
    private String redisTimeout;
    
    public RedisConfigDebugger(Environment environment) {
        this.environment = environment;
    }
    
    @PostConstruct
    public void debugRedisConfiguration() {
        logger.info("=== REDIS CONFIGURATION DEBUG ===");
        logger.info("Active profiles: {}", String.join(",", environment.getActiveProfiles()));
        logger.info("spring.redis.host from @Value: {}", redisHost);
        logger.info("spring.redis.port from @Value: {}", redisPort);
        logger.info("spring.redis.timeout from @Value: {}", redisTimeout);
        
        logger.info("spring.redis.host from Environment: {}", environment.getProperty("spring.redis.host"));
        logger.info("spring.redis.port from Environment: {}", environment.getProperty("spring.redis.port"));
        logger.info("spring.redis.timeout from Environment: {}", environment.getProperty("spring.redis.timeout"));
        
        // Check all redis-related properties
        logger.info("All spring.redis.* properties:");
        System.getProperties().entrySet().stream()
            .filter(entry -> entry.getKey().toString().startsWith("spring.redis"))
            .forEach(entry -> logger.info("  {}: {}", entry.getKey(), entry.getValue()));
            
        // Check property sources
        logger.info("Property sources order:");
        if (environment instanceof org.springframework.core.env.AbstractEnvironment) {
            org.springframework.core.env.AbstractEnvironment abstractEnv = (org.springframework.core.env.AbstractEnvironment) environment;
            abstractEnv.getPropertySources().forEach(ps -> 
                logger.info("  - {}", ps.getName())
            );
        }
        logger.info("=== END REDIS CONFIGURATION DEBUG ===");
    }
}
