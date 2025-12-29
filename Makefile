# Makefile for CodePromptu
# Spring Boot microservices example implementation

SHELL := /bin/zsh

# Directories
SRC_DIR := src
DOCS_DIR := docs
MEMORY_BANK_DIR := memory-bank

# Maven configuration
MVN := mvn
MVN_OPTS := -f $(SRC_DIR)/pom.xml

.PHONY: all help verify build clean test install package run

# Default target
all: verify

# Help
help:
	@echo "CodePromptu - Available targets:"
	@echo ""
	@echo "  make verify    - Verify prerequisites and setup"
	@echo "  make build     - Build all microservices"
	@echo "  make test      - Run all tests"
	@echo "  make package   - Package all microservices as JARs"
	@echo "  make clean     - Clean build artifacts"
	@echo "  make install   - Install artifacts to local Maven repository"
	@echo ""
	@echo "Microservices:"
	@echo "  - config-server: Spring Cloud Config Server"
	@echo "  - gateway: API Gateway"
	@echo "  - api: CodePromptu API Service"
	@echo "  - shared: Shared utilities and domain models"

# Verify prerequisites
verify: check-java check-maven check-pom
	@echo "✅ All prerequisites verified!"

check-java:
	@echo -n "Checking Java... "
	@which java > /dev/null 2>&1 || (echo "❌ FAILED"; echo "Install: brew install openjdk@17"; exit 1)
	@java -version 2>&1 | head -1
	@echo "✅ Java found"

check-maven:
	@echo -n "Checking Maven... "
	@which mvn > /dev/null 2>&1 || (echo "❌ FAILED"; echo "Install: brew install maven"; exit 1)
	@mvn --version | head -1
	@echo "✅ Maven found"

check-pom:
	@echo -n "Checking Maven project... "
	@if [ -f "$(SRC_DIR)/pom.xml" ]; then \
		echo "✅ Maven project found"; \
	else \
		echo "❌ FAILED - pom.xml not found in $(SRC_DIR)/"; \
		exit 1; \
	fi

# Build targets
build: verify
	@echo "🏗️  Building CodePromptu microservices..."
	cd $(SRC_DIR) && $(MVN) compile
	@echo "✅ Build complete!"

package: verify
	@echo "📦 Packaging CodePromptu microservices..."
	cd $(SRC_DIR) && $(MVN) package -DskipTests
	@echo "✅ Package complete!"

install: verify
	@echo "📦 Installing CodePromptu to local Maven repository..."
	cd $(SRC_DIR) && $(MVN) install -DskipTests
	@echo "✅ Install complete!"

# Testing
test: verify
	@echo "🧪 Running all tests..."
	cd $(SRC_DIR) && $(MVN) test
	@echo "✅ Tests complete!"

test-integration: verify
	@echo "🧪 Running integration tests..."
	cd $(SRC_DIR) && $(MVN) verify
	@echo "✅ Integration tests complete!"

# Clean
clean:
	@echo "🧹 Cleaning CodePromptu artifacts..."
	@if [ -f "$(SRC_DIR)/pom.xml" ]; then \
		cd $(SRC_DIR) && $(MVN) clean; \
	fi
	@echo "✅ Clean complete!"

# Development helpers
compile: verify
	@echo "🔨 Compiling source code..."
	cd $(SRC_DIR) && $(MVN) compile
	@echo "✅ Compile complete!"

validate: verify
	@echo "🔍 Validating Maven project..."
	cd $(SRC_DIR) && $(MVN) validate
	@echo "✅ Validation complete!"

# Documentation
check-docs:
	@echo "📚 Checking documentation..."
	@if [ -d "$(DOCS_DIR)" ]; then \
		echo "✅ Documentation directory found"; \
		ls -1 $(DOCS_DIR)/*.md 2>/dev/null | wc -l | xargs -I {} echo "   {} markdown files"; \
	else \
		echo "⚠️  Documentation directory not found"; \
	fi

check-memory-bank:
	@echo "🧠 Checking memory bank..."
	@if [ -d "$(MEMORY_BANK_DIR)" ]; then \
		echo "✅ Memory bank found"; \
		ls -1 $(MEMORY_BANK_DIR)/*.md 2>/dev/null | wc -l | xargs -I {} echo "   {} memory bank files"; \
	else \
		echo "⚠️  Memory bank directory not found"; \
	fi

# Module-specific builds (optional)
build-shared: verify
	@echo "🏗️  Building shared module..."
	cd $(SRC_DIR)/shared && $(MVN) compile

build-config: verify
	@echo "🏗️  Building config server..."
	cd $(SRC_DIR)/config && $(MVN) compile

build-gateway: verify
	@echo "🏗️  Building gateway..."
	cd $(SRC_DIR)/gateway && $(MVN) compile

build-api: verify
	@echo "🏗️  Building api service..."
	cd $(SRC_DIR)/api && $(MVN) compile

# Run services (requires prerequisites like PostgreSQL, Redis)
run-config:
	@echo "🚀 Starting Config Server..."
	cd $(SRC_DIR)/config && $(MVN) spring-boot:run

run-gateway:
	@echo "🚀 Starting API Gateway..."
	cd $(SRC_DIR)/gateway && $(MVN) spring-boot:run

run-api:
	@echo "🚀 Starting API Service..."
	cd $(SRC_DIR)/api && $(MVN) spring-boot:run
