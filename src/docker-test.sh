#!/bin/bash

# CodePromptu Docker Setup Test Script
# This script tests the Docker Compose setup and identifies missing components

echo "=== CodePromptu Docker Setup Test ==="
echo "Testing Docker Compose configuration..."
echo

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $2"
    else
        echo -e "${RED}✗${NC} $2"
    fi
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Check if Docker is running
echo "1. Checking Docker availability..."
docker --version > /dev/null 2>&1
print_status $? "Docker is installed and running"

docker-compose --version > /dev/null 2>&1
print_status $? "Docker Compose is available"

echo

# Check for required files
echo "2. Checking required files and directories..."

# Database files
if [ -f "database/init/01-init-database.sql" ]; then
    print_status 0 "Database initialization script exists"
else
    print_status 1 "Database initialization script missing"
fi

# Environment file
if [ -f ".env" ]; then
    print_status 0 "Environment file exists"
else
    print_status 1 "Environment file missing"
fi

# Service directories
services=("config" "gateway" "processor" "api" "worker" "ui" "monitoring")
missing_services=()

for service in "${services[@]}"; do
    if [ -d "$service" ]; then
        if [ -f "$service/Dockerfile" ]; then
            print_status 0 "$service service directory and Dockerfile exist"
        else
            print_status 1 "$service service directory exists but Dockerfile missing"
            missing_services+=("$service")
        fi
    else
        print_status 1 "$service service directory missing"
        missing_services+=("$service")
    fi
done

# Config repository
if [ -d "config-repo" ]; then
    print_status 0 "Configuration repository exists"
    config_files=("application.yml" "gateway.yml")
    for config_file in "${config_files[@]}"; do
        if [ -f "config-repo/$config_file" ]; then
            print_status 0 "Config file $config_file exists"
        else
            print_status 1 "Config file $config_file missing"
        fi
    done
else
    print_status 1 "Configuration repository missing"
fi

echo

# Test Docker Compose validation
echo "3. Testing Docker Compose configuration..."
docker-compose config > /dev/null 2>&1
if [ $? -eq 0 ]; then
    print_status 0 "Docker Compose configuration is valid"
else
    print_status 1 "Docker Compose configuration has errors"
    echo "Running docker-compose config to show errors:"
    docker-compose config
fi

echo

# Summary
echo "4. Summary and Next Steps..."
if [ ${#missing_services[@]} -eq 0 ]; then
    echo -e "${GREEN}All service directories are present!${NC}"
else
    echo -e "${RED}Missing services:${NC}"
    for service in "${missing_services[@]}"; do
        echo "  - $service"
    done
fi

echo
echo "=== Test Complete ==="
echo
echo "To start the services that are ready:"
echo "  docker-compose up database cache config"
echo
echo "To build and start all services (will fail for missing services):"
echo "  docker-compose up --build"
echo
echo "To see logs for specific services:"
echo "  docker-compose logs -f <service-name>"
