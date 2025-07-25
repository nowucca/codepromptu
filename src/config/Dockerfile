FROM openjdk:17-jdk-slim

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the pre-built JAR file
COPY target/config-server.jar app.jar

# Run the application
EXPOSE 8888
CMD ["java", "-jar", "app.jar"]
