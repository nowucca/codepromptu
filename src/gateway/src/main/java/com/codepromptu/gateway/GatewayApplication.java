package com.codepromptu.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {
    RedisAutoConfiguration.class,
    RedisReactiveAutoConfiguration.class
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Programmatic route configuration as backup to YAML configuration
     * This provides additional flexibility for dynamic routing if needed
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Health check route - direct to gateway actuator
                .route("health-route", r -> r.path("/health")
                        .uri("http://localhost:8080/actuator/health"))
                
                // API service routes
                .route("api-prompts", r -> r.path("/api/prompts/**")
                        .uri("http://api:8081"))
                .route("api-templates", r -> r.path("/api/templates/**")
                        .uri("http://api:8081"))
                .route("api-evaluations", r -> r.path("/api/evaluations/**")
                        .uri("http://api:8081"))
                
                // Processor service routes
                .route("processor-analyze", r -> r.path("/processor/analyze/**")
                        .uri("http://processor:8082"))
                .route("processor-cluster", r -> r.path("/processor/cluster/**")
                        .uri("http://processor:8082"))
                
                // Worker service routes
                .route("worker-jobs", r -> r.path("/worker/jobs/**")
                        .uri("http://worker:8084"))
                .route("worker-batch", r -> r.path("/worker/batch/**")
                        .uri("http://worker:8084"))
                
                .build();
    }
}
