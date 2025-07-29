FROM openjdk:17-jdk-slim

WORKDIR /app

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Copy the built JAR file from the build context
COPY target/api-1.0.0-SNAPSHOT.jar app.jar

# Run the application
EXPOSE 8081
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
