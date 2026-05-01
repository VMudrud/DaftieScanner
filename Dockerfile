# Stage 1 — build
FROM maven:3.9-eclipse-temurin-25 AS builder

# The maven Docker image sets MAVEN_CONFIG=/root/.m2 (repo path), but mvnw
# passes $MAVEN_CONFIG as raw CLI args to Maven, causing a lifecycle phase error.
ENV MAVEN_CONFIG=""

WORKDIR /build

# Cache dependencies before copying source
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN ./mvnw dependency:go-offline -q

# Build the application
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2 — runtime
FROM eclipse-temurin:25-jre AS runtime

# Install AWS CLI v2 for SSM parameter fetching in entrypoint
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
    && unzip -q /tmp/awscliv2.zip -d /tmp \
    && /tmp/aws/install \
    && rm -rf /tmp/awscliv2.zip /tmp/aws \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /build/target/daftiescanner-0.0.1-SNAPSHOT.jar app.jar
COPY entrypoint.sh ./
RUN chmod +x entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["./entrypoint.sh"]
