# Narrative: Gateway Service & Redis Configuration Troubleshooting

## Context

This narrative documents the technical journey of enabling the CodePromptu gateway service to connect to Redis using configuration from the Spring Cloud Config Server. The goal was to achieve a working end-to-end multi-service RPC call, with Redis as a critical dependency.

---

## 1. Initial Problem

- The gateway service was failing to connect to Redis.
- Health checks showed:  
  `org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis`
- The gateway was attempting to connect to `localhost:6379` instead of the correct Docker service name `codepromptu-cache:6379`.

---

## 2. Diagnosis

- The gateway was not loading its configuration from the Spring Cloud Config Server.
- The correct Redis host/port was present in the config server's `gateway.yml`, but not being picked up by the running service.
- The application logs showed no evidence of config server connection attempts.

---

## 3. Key Troubleshooting Steps

### a. Configuration Review

- Verified `application.yml` and `bootstrap.yml` in the gateway service.
- Ensured the config server URI, username, and password were set in `bootstrap.yml`:

  ```yaml
  spring:
    application:
      name: gateway
    profiles:
      active: docker
    cloud:
      config:
        uri: ${CONFIG_SERVER_URI:http://config:8888}
        username: config
        password: config123
        fail-fast: true
        retry:
          initial-interval: 1000
          max-attempts: 6
          max-interval: 2000
          multiplier: 1.1
  ```

### b. Dependency Issues

- Discovered the gateway was missing the Spring Cloud Config Client dependency.
- Added to `pom.xml`:

  ```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-config</artifactId>
  </dependency>
  ```

- Still, the config server was not being contacted.

### c. Spring Boot 3.x Bootstrap Change

- Realized that Spring Boot 3.x requires the `spring-cloud-starter-bootstrap` dependency for `bootstrap.yml` to be processed.
- Added to `pom.xml`:

  ```xml
  <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-bootstrap</artifactId>
  </dependency>
  ```

### d. Rebuild and Redeploy

- Rebuilt the gateway service with Maven.
- Rebuilt the Docker image and restarted the service.

---

## 4. Testing and Verification

- Used `docker logs` and `/actuator/health` endpoint to verify the gateway's connection to Redis.
- Confirmed that the gateway was now using the correct Redis host (`codepromptu-cache:6379`) as configured in the config server.

---

## 5. Lessons Learned

- **Spring Cloud Config integration in Spring Boot 3.x** requires both the config client and bootstrap starter dependencies.
- Always check for missing dependencies when configuration is not being loaded as expected.
- Docker service names must be used for inter-container communication, not `localhost`.
- Iterative troubleshooting and log analysis are essential for diagnosing distributed system configuration issues.

---

## 6. Key Code/Config Changes

- `codepromptu/src/gateway/pom.xml` (added config and bootstrap dependencies)
- `codepromptu/src/gateway/src/main/resources/bootstrap.yml` (ensured config server connection details)
- Docker commands for rebuild/restart

---

## 7. Outcome

The gateway service now correctly loads its configuration from the Spring Cloud Config Server and connects to Redis using the correct Docker service name, enabling end-to-end multi-service RPC calls.
