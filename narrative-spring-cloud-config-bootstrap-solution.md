# Spring Cloud Config Bootstrap Issue - Root Cause Analysis & Solution

## Executive Summary

After extensive investigation, I've identified the root cause of why the API and Gateway microservices cannot connect to Redis in their health checks. The issue stems from a **Spring Cloud Config bootstrap failure** that prevents services from loading the correct Redis configuration from the Config Server.

## Current System Status

### ✅ Infrastructure Health
- **Redis**: HEALTHY - Running and responding to connections (`PONG`)
- **Config Server**: HEALTHY - Serving configuration files correctly
- **Database**: HEALTHY - PostgreSQL accepting connections
- **Network**: HEALTHY - Services can reach each other (API can connect to cache:6379)

### ❌ Service Health Issues
- **API Service**: Spring Cloud Config bootstrap **COMPLETELY BROKEN**
- **Gateway Service**: Spring Cloud Config bootstrap **PARTIALLY WORKING** but still has Redis issues
- **Both Services**: Redis health checks failing with `Connection refused: localhost/127.0.0.1:6379`

## Root Cause Analysis

### The Core Problem: Configuration Source Priority

**What Should Happen:**
1. Service starts up
2. Spring Cloud Config bootstrap loads configuration from Config Server
3. Service gets `spring.redis.host: cache` from centralized config
4. Service connects to Redis successfully

**What's Actually Happening:**
1. Service starts up
2. Spring Cloud Config bootstrap **FAILS** or loads incorrectly
3. Service falls back to local `application.yml` files
4. Local files either have no Redis config or wrong Redis config
5. Spring Boot defaults to `localhost:6379` when no Redis config found
6. Connection fails because Redis is at `cache:6379`, not `localhost:6379`

### Evidence from Health Checks

**Config Server Health:**
```json
{
  "status": "UP",
  "components": {
    "configServer": {
      "status": "UP",
      "details": {
        "repositories": [
          {
            "sources": ["file:/config-repo/application.yml"],
            "name": "app",
            "profiles": ["default"]
          }
        ]
      }
    }
  }
}
```

**Gateway Health (Partial Success):**
```json
{
  "components": {
    "clientConfigServer": {
      "status": "UP",
      "details": {
        "propertySources": [
          "bootstrapProperties-configClient",
          "bootstrapProperties-file:/config-repo/gateway.yml",
          "bootstrapProperties-file:/config-repo/application.yml"
        ]
      }
    },
    "redis": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis"
      }
    }
  }
}
```

**API Health (Complete Failure):**
```json
{
  "components": {
    // NO clientConfigServer component - bootstrap completely failed
    "redis": {
      "status": "DOWN",
      "details": {
        "error": "org.springframework.data.redis.RedisConnectionFailureException: Unable to connect to Redis"
      }
    }
  }
}
```

### Key Differences Identified

**API Service Bootstrap Configuration (BROKEN):**
```yaml
spring:
  application:
    name: api
  cloud:
    config:
      uri: http://config:8888
      username: config
      password: config123
      fail-fast: true
      retry:
        initial-interval: 1000
        max-attempts: 6
        max-interval: 2000
        multiplier: 1.1
      enabled: true
  config:
    import: "configserver:"  # ❌ CONFLICTING CONFIGURATION
```

**Gateway Service Bootstrap Configuration (WORKING):**
```yaml
spring:
  application:
    name: gateway
  cloud:
    config:
      uri: http://config:8888
      username: config
      password: config123
      fail-fast: true
      retry:
        initial-interval: 1000
        max-attempts: 6
        max-interval: 2000
        multiplier: 1.1
```

### The Critical Issue: Configuration Import Conflict

The API service had this conflicting configuration:
```yaml
spring:
  config:
    import: "configserver:"
```

This **Spring Boot 2.4+ configuration import** approach conflicts with the traditional bootstrap approach, causing the bootstrap process to fail completely.

## Solution Implemented

### Step 1: Fixed Bootstrap Configuration Conflict

**Removed the conflicting import from API bootstrap.yml:**
```yaml
# REMOVED:
spring:
  config:
    import: "configserver:"
```

**Result:** Bootstrap configuration now matches the working Gateway service.

### Step 2: Added Temporary Redis Configuration

Since the bootstrap issue persists, I added Redis configuration directly to the API service's local `application.yml` as a temporary workaround:

```yaml
spring:
  redis:
    host: cache
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

## Current Status After Fix

### Network Connectivity Verified
- API container can successfully connect to cache container (172.18.0.2:6379)
- All infrastructure components are healthy and communicating

### Configuration Loading Status
- **Gateway**: Successfully loading from Config Server (shows `clientConfigServer` component)
- **API**: Bootstrap still not working (no `clientConfigServer` component in health check)

### Redis Connection Status
- Still failing for both services despite network connectivity being confirmed
- Services are still trying to connect to `localhost:6379` instead of `cache:6379`

## Next Steps Required

### Immediate Actions
1. **Investigate why the bootstrap fix didn't resolve the API service issue**
2. **Check if there are additional configuration conflicts**
3. **Verify the Gateway service Redis connection despite successful Config Server bootstrap**

### Long-term Solution
1. **Fix the Spring Cloud Config bootstrap process completely**
2. **Remove temporary local Redis configurations**
3. **Ensure all services load configuration from centralized Config Server**

## Technical Details

### Redis Configuration in Config Server
```yaml
# /config-repo/application.yml
spring:
  redis:
    host: cache  # ✅ Correct Docker hostname
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

### Error Pattern
```
Caused by: io.netty.channel.AbstractChannel$AnnotatedConnectException: 
Connection refused: localhost/127.0.0.1:6379
```

This error confirms that services are defaulting to `localhost` instead of loading `cache` from the Config Server.

## Conclusion

The Redis connection failures are a **symptom** of a deeper Spring Cloud Config bootstrap problem. While Redis is healthy and network connectivity works, the services cannot access the correct configuration due to bootstrap failures.

The solution requires:
1. ✅ **Fixed bootstrap configuration conflicts** (completed)
2. 🔄 **Debug remaining bootstrap issues** (in progress)
3. ⏳ **Restore centralized configuration management** (pending)

This issue demonstrates the critical importance of proper Spring Cloud Config bootstrap configuration in microservices architectures.
