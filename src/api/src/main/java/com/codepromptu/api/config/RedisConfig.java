package com.codepromptu.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import jakarta.annotation.PostConstruct;

@Configuration
public class RedisConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);
    
    @Value("${spring.redis.host:cache}")
    private String redisHost;
    
    @Value("${spring.redis.port:6379}")
    private int redisPort;
    
    @Value("${spring.redis.timeout:2000ms}")
    private String redisTimeout;
    
    @PostConstruct
    public void logRedisConfiguration() {
        logger.info("=== API REDIS CONFIGURATION ===");
        logger.info("Redis Host: {}", redisHost);
        logger.info("Redis Port: {}", redisPort);
        logger.info("Redis Timeout: {}", redisTimeout);
        logger.info("Using cache name '{}' instead of localhost", redisHost);
    }
    
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        logger.info("Creating Redis Connection Factory for API service");
        logger.info("Connecting to Redis at {}:{}", redisHost, redisPort);
        
        // Use RedisStandaloneConfiguration for better control
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);  // This will be "cache" from config, not localhost
        config.setPort(redisPort);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setValidateConnection(true);
        
        logger.info("Redis Connection Factory created successfully for host: {}", redisHost);
        return factory;
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        logger.info("Creating StringRedisTemplate bean for API service");
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory());
        template.afterPropertiesSet();
        logger.info("StringRedisTemplate created successfully for API service");
        return template;
    }
}
