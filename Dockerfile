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

# Runtime stage - using Ubuntu base for Chrome for Testing
FROM ubuntu:22.04

# Install dependencies
RUN apt-get update && \
    apt-get install -y \
    openjdk-17-jre-headless \
    openssh-client \
    wget \
    unzip \
    curl \
    gnupg \
    software-properties-common \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Install Chrome for Testing
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -y google-chrome-stable && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create a non-root user for running the application
RUN useradd -m -s /bin/bash appuser

# Set JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Set working directory
WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/lilhotelier.jar lilhotelier.jar

# Create SSH directory structure for appuser
RUN mkdir -p /home/appuser/.ssh && \
    chmod 700 /home/appuser/.ssh

# Change ownership to the appuser
RUN chown -R appuser:appuser /app /home/appuser/.ssh

# Switch to appuser for security
USER appuser

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Selenium configuration for headless Chrome
ENV DISPLAY=:99
ENV SE_SCREEN_WIDTH=1920
ENV SE_SCREEN_HEIGHT=1080

# Set SSH key permissions (this will be done at runtime after volume mount)
ENV SSH_KEY_PERMISSIONS_SET=false

# Set Chrome for Testing binary path
ENV CHROME_BINARY_PATH=/usr/bin/google-chrome-stable

# Debug entry point - uncomment for debugging (sleep for 1 hour)
#CMD ["sleep", "3600"]

# Run the application with config directory and processor ID from environment
CMD ["sh", "-c", "java -server $JAVA_OPTS -Dchrome.binary.path=$CHROME_BINARY_PATH -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar lilhotelier.jar com.macbackpackers.RunProcessor -S"]
