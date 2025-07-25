#!/bin/bash

# CodePromptu Docker Status Script
# Reports running status and health of all services defined in docker-compose.yml

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== CodePromptu Docker Status ==="
echo

# List of all services as per docker-compose.yml
services=(
    "codepromptu-database"
    "codepromptu-cache"
    "codepromptu-config"
    "codepromptu-gateway"
    "codepromptu-processor"
    "codepromptu-api"
    "codepromptu-worker"
    "codepromptu-ui"
    "codepromptu-monitoring"
)

for svc in "${services[@]}"; do
    status=$(docker inspect --format '{{.State.Status}}' "$svc" 2>/dev/null || echo "notfound")
    if [ "$status" == "running" ]; then
        # Try to get health status
        health=$(docker inspect --format '{{json .State.Health.Status}}' "$svc" 2>/dev/null | tr -d '"')
        if [ "$health" == "healthy" ]; then
            echo -e "${GREEN}✓ $svc: running, healthy${NC}"
        elif [ "$health" == "unhealthy" ]; then
            echo -e "${RED}✗ $svc: running, UNHEALTHY${NC}"
        elif [ -n "$health" ] && [ "$health" != "null" ]; then
            echo -e "${YELLOW}⚠ $svc: running, health: $health${NC}"
        else
            echo -e "${GREEN}✓ $svc: running${NC}"
        fi
    elif [ "$status" == "exited" ]; then
        echo -e "${YELLOW}⚠ $svc: exited${NC}"
    elif [ "$status" == "created" ]; then
        echo -e "${YELLOW}⚠ $svc: created (not started)${NC}"
    else
        echo -e "${RED}✗ $svc: not running or not found${NC}"
    fi
done

echo
echo "=== Status Check Complete ==="
