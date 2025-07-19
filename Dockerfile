# Multi-stage build
# Stage 1: Build the application
FROM eclipse-temurin:24-jdk AS build

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:24-jre

WORKDIR /app

# Create logs directory
RUN mkdir -p /app/logs

# Copy the built jar from build stage
COPY --from=build /app/target/rag-confluence-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Set JVM options and run the application
ENV JAVA_OPTS="-Xmx2g -Xms1g"
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]