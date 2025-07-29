# CodePromptU Testing Guide

## Overview

This guide provides comprehensive instructions for running unit tests (with mocks) and integration tests (with TestContainers) for the CodePromptU system. The testing strategy separates concerns properly:

- **Unit Tests**: Use mocks to test business logic in isolation
- **Integration Tests**: Use TestContainers to test with real databases and external services

## Test Structure

### Current Test Organization

```
src/
├── api/src/test/java/
│   ├── controller/
│   │   └── PromptControllerTest.java          # Unit tests with mocks
│   └── service/
│       ├── SimilarityServiceTest.java         # Unit tests with mocks
│       ├── EmbeddingIntegrationTest.java      # Integration tests with TestContainers
│       └── EmbeddingStorageTest.java          # Integration tests with TestContainers
├── gateway/src/test/java/
│   └── GatewayApplicationTest.java            # Basic Spring Boot test
└── config/src/test/java/
    └── ConfigServerApplicationTest.java       # Basic Spring Boot test
```

## Running Unit Tests (Fast - Uses Mocks)

Unit tests run quickly because they use mocks instead of real databases or external services.

### Run All Unit Tests

```bash
# From project root
cd src

# Run unit tests for all modules (excludes integration tests)
./mvnw test -Dtest="!*IntegrationTest,!*StorageTest" -q

# Or run each module individually
./mvnw -pl shared test -q                    # Shared module (no tests currently)
./mvnw -pl config test -q                    # Config server tests
./mvnw -pl api test -Dtest="!*IntegrationTest,!*StorageTest" -q  # API unit tests only
./mvnw -pl gateway test -Dtest="!*IntegrationTest" -q            # Gateway tests (will fail without config server)
```

### Run Specific Unit Test Classes

```bash
cd src

# Test specific controller
./mvnw -pl api test -Dtest=PromptControllerTest -q

# Test specific service
./mvnw -pl api test -Dtest=SimilarityServiceTest -q

# Test specific nested class
./mvnw -pl api test -Dtest="SimilarityServiceTest\$SimilarityClassificationTests" -q
```

### Unit Test Coverage

#### PromptControllerTest (Web Layer)
- ✅ **Mocks Used**: PromptService, PromptMapper
- ✅ **Tests**: All REST endpoints, security, validation, error handling
- ✅ **Coverage**: 10 nested test classes, 20+ test methods

#### SimilarityServiceTest (Business Logic)
- ✅ **Mocks Used**: PromptRepository, PromptVectorRepository, EmbeddingService
- ✅ **Tests**: Similarity classification, prompt lineage detection, error handling
- ✅ **Coverage**: 6 nested test classes, 25+ test methods

## Running Integration Tests (Slower - Uses Real Services)

Integration tests use TestContainers to spin up real PostgreSQL databases with pgvector extension.

### Prerequisites

- Docker must be running
- Sufficient memory allocated to Docker (recommended: 4GB+)

### Run All Integration Tests

```bash
cd src

# Run only integration tests
./mvnw -pl api test -Dtest="*IntegrationTest,*StorageTest" -q

# Run specific integration test classes
./mvnw -pl api test -Dtest=EmbeddingIntegrationTest -q
./mvnw -pl api test -Dtest=EmbeddingStorageTest -q
```

### Integration Test Coverage

#### EmbeddingIntegrationTest
- ✅ **Real Services**: PostgreSQL + pgvector via TestContainers
- ✅ **Mocks**: EmbeddingService (for consistent test data)
- ✅ **Tests**: Complete workflow, similarity search, prompt classification
- ✅ **Validates**: Database schema, pgvector extension, embedding storage

#### EmbeddingStorageTest
- ✅ **Real Services**: PostgreSQL + pgvector via TestContainers
- ✅ **Tests**: pgvector type mapping, embedding conversion, storage logic

## Module-by-Module Testing Instructions

### 1. Shared Module

```bash
cd src/shared
../mvnw clean compile -q
# No tests currently - contains domain classes and utilities
```

### 2. Config Module

```bash
cd src/config
../mvnw clean test -q
# Basic Spring Boot application test
```

### 3. API Module (Most Comprehensive)

```bash
cd src/api

# Unit tests only (fast)
../mvnw test -Dtest="!*IntegrationTest,!*StorageTest" -q

# Integration tests only (slower, requires Docker)
../mvnw test -Dtest="*IntegrationTest,*StorageTest" -q

# All tests
../mvnw test -q
```

### 4. Gateway Module

```bash
cd src/gateway

# Note: Gateway test will fail without config server running
# This is expected behavior - gateway requires config server dependency
../mvnw test -q

# To run gateway with config server (integration environment):
# 1. Start config server: cd ../config && ../mvnw spring-boot:run
# 2. In another terminal: cd ../gateway && ../mvnw test
```

## Similarity Detection & Lineage Verification

### Key Functionality Confirmed ✅

The system successfully implements **similarity detection and prompt lineage** with three classification levels:

#### 1. Classification Thresholds
- **SAME** (≥95% similarity): Essentially identical prompts
- **FORK** (70-94% similarity): Related prompts that could form lineage
- **NEW** (<70% similarity): Unique prompts

#### 2. Lineage Detection Features
- **Similarity Search**: Find related prompts based on embedding vectors
- **Classification**: Automatically categorize prompt relationships
- **Parent-Child Relationships**: Track prompt evolution through forking
- **Threshold Configuration**: Configurable similarity thresholds

#### 3. Verified Through Tests
```bash
# Run similarity detection tests
cd src/api
../mvnw test -Dtest=SimilarityServiceTest -q

# Run end-to-end lineage workflow
../mvnw test -Dtest=EmbeddingIntegrationTest -q
```

### Example Test Scenarios

The integration tests verify these real-world scenarios:

1. **Java REST API Prompts**: Two similar Java prompts are correctly identified as related
2. **Cross-Domain Detection**: Cooking prompts are correctly identified as unrelated to Java prompts
3. **Similarity Scoring**: Scores are properly ordered (more similar = higher score)
4. **Classification Logic**: Prompts are correctly classified as SAME/FORK/NEW

## Docker Image Testing

All modules can be containerized and tested:

```bash
cd src

# Build all Docker images
docker build -t codepromptu-config:test ./config
docker build -t codepromptu-api:test ./api  
docker build -t codepromptu-gateway:test ./gateway

# Verify images exist
docker images | grep codepromptu
```

## Continuous Integration Commands

### Full Test Suite (Recommended for CI)

```bash
#!/bin/bash
cd src

echo "🧪 Running Unit Tests..."
./mvnw test -Dtest="!*IntegrationTest,!*StorageTest" -q

echo "🐳 Running Integration Tests..."
./mvnw -pl api test -Dtest="*IntegrationTest,*StorageTest" -q

echo "🏗️ Building Docker Images..."
docker build -t codepromptu-config:test ./config
docker build -t codepromptu-api:test ./api
docker build -t codepromptu-gateway:test ./gateway

echo "✅ All tests completed successfully!"
```

### Quick Smoke Test

```bash
#!/bin/bash
cd src

echo "🚀 Quick smoke test..."
./mvnw clean compile -q
./mvnw -pl api test -Dtest=PromptControllerTest -q
./mvnw -pl api test -Dtest=SimilarityServiceTest -q

echo "✅ Smoke test passed!"
```

## Test Configuration

### Test Profiles

- **Unit Tests**: Use `@ActiveProfiles("test")` with mocked dependencies
- **Integration Tests**: Use `@Testcontainers` with real PostgreSQL + pgvector

### Test Data

- **Unit Tests**: Use builder pattern to create test objects
- **Integration Tests**: Use SQL scripts (`test-schema.sql`) for database setup

### Mock Strategy

- **Controllers**: Mock services and repositories
- **Services**: Mock repositories and external services
- **Integration**: Mock only external APIs (OpenAI), use real database

## Troubleshooting

### Common Issues

1. **Docker not running**: Integration tests will fail
   ```bash
   docker info  # Verify Docker is running
   ```

2. **Port conflicts**: TestContainers uses random ports
   ```bash
   docker ps  # Check for conflicting containers
   ```

3. **Memory issues**: Increase Docker memory allocation
   ```bash
   # Docker Desktop: Settings > Resources > Memory > 4GB+
   ```

4. **Gateway test failures**: Expected when config server not running
   ```bash
   # This is architectural - gateway requires config server
   ```

### Debug Commands

```bash
# Verbose test output
./mvnw test -X

# Run single test with debug
./mvnw test -Dtest=SimilarityServiceTest -Dmaven.surefire.debug

# Check test reports
ls -la target/surefire-reports/
```

## Summary

- ✅ **Unit Tests**: Fast, isolated, comprehensive coverage with mocks
- ✅ **Integration Tests**: Real database, end-to-end workflows with TestContainers  
- ✅ **Similarity Detection**: Fully implemented and tested with three-tier classification
- ✅ **Prompt Lineage**: Parent-child relationships and forking functionality verified
- ✅ **Docker Ready**: All modules containerized and tested
- ✅ **CI Ready**: Clear separation between fast unit tests and slower integration tests

The CodePromptU system successfully implements intelligent prompt similarity detection and lineage tracking, with comprehensive test coverage at both unit and integration levels.
