# Narrative: Debugging and Fixing Spring Cloud Config Client URI for Gateway

## Context

The CodePromptu system uses a Spring Cloud Gateway service that must connect to a Spring Cloud Config server running in Docker Compose. The config server is exposed as the service name `config` on port 8888. The gateway must use `http://config:8888` as its config server URI when running in Docker Compose.

## Problem

Despite all configuration attempts, the gateway repeatedly tried to connect to `http://localhost:8888` for its config server, resulting in connection refused errors and failed startup. This persisted even after setting the URI in:

- `application.yml` (with profiles and environment variable indirection)
- `bootstrap.yml` (hardcoded and with environment variable indirection)
- Docker Compose environment variables (`SPRING_CLOUD_CONFIG_URI`, `CONFIG_SERVER_URI`)
- Dockerfile and Docker Compose command-line overrides (`JAVA_OPTS`, explicit `-Dspring.cloud.config.uri`)
- Full Docker system prune and rebuilds

## Root Causes and Fixes

### 1. Spring Config Import and Profile Precedence

- The `spring.config.import` property in `application.yml` was removed, as it was not being picked up early enough and conflicted with the config client bootstrap process.
- The config server URI was set in `bootstrap.yml` at the top level, not under any profile, to ensure it is always used.

### 2. Docker Compose and Java System Properties

- The Dockerfile was updated to use `CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]` so that `JAVA_OPTS` from the environment could be used.
- Docker Compose was updated to set `JAVA_OPTS=-Dspring.cloud.config.uri=http://config:8888` and the command was also set explicitly to ensure the override.

### 3. Debugging Output

- A debug print statement was added to the `main` method of `GatewayApplication.java` to print the value of `spring.cloud.config.uri` at runtime, confirming the override was effective.

### 4. YAML Structure Error

- The error `Failed to bind properties under 'spring.config' to org.springframework.boot.context.config.ConfigDataProperties` was caused by empty or invalid `spring.config:` blocks in `application.yml`. These were removed to resolve the error.

### 5. Full Docker Prune

- All containers, images, networks, and volumes were pruned to ensure no stale configuration or images were being used.

## Resolution

- The gateway now prints `spring.cloud.config.uri = http://config:8888` at startup, confirming the override is effective.
- The logs show successful fetching of config from the config server and correct profile activation.
- All debugging code was removed after confirming the fix.

## Key Files Changed

- `codepromptu/src/gateway/src/main/resources/application.yml`
- `codepromptu/src/gateway/src/main/resources/bootstrap.yml`
- `codepromptu/src/gateway/Dockerfile`
- `codepromptu/src/docker-compose.yml`
- `codepromptu/src/gateway/src/main/java/com/codepromptu/gateway/GatewayApplication.java` (debug print, then removed)

## Lessons Learned

- Spring Cloud Config client property resolution is highly sensitive to the order and location of configuration.
- Always ensure the config server URI is set at the highest precedence possible, and avoid empty or invalid YAML blocks.
- Use explicit command-line overrides and debug output to confirm runtime configuration.
- Prune Docker resources to avoid stale images and containers during deep debugging.
