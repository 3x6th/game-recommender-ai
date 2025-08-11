FROM maven:3.9.5-openjdk-21 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml and proto files
COPY pom.xml .
COPY ../../contracts/proto ./contracts/proto/

# Download dependencies and generate gRPC code
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:21-jre-slim

# Set working directory
WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Set environment variables
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV GRPC_AI_SERVICE_HOST=ai-service
ENV GRPC_AI_SERVICE_PORT=9090

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
