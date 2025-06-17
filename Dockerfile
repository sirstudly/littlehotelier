# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application and copy dependencies
RUN mvn clean package -DskipTests

# Runtime stage - using Selenium standalone image with Chrome
FROM selenium/standalone-chrome:latest

# Switch to root to install Java and make system changes
USER root

# Install OpenJDK 17
RUN apt-get update && \
    apt-get install -y openjdk-17-jre-headless && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/lilhotelier.jar lilhotelier.jar

# Create logs directory
RUN mkdir -p logs

# Change ownership to the selenium user
RUN chown -R seluser:seluser /app

# Switch back to selenium user for security
USER seluser

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Selenium configuration for headless Chrome
ENV DISPLAY=:99
ENV SE_SCREEN_WIDTH=1920
ENV SE_SCREEN_HEIGHT=1080

# Debug entry point - uncomment for debugging (sleep for 1 hour)
#CMD ["sleep", "3600"]

# Run the application with config directory and processor ID from environment
CMD ["sh", "-c", "java -server $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar lilhotelier.jar com.macbackpackers.RunProcessor -S"]
