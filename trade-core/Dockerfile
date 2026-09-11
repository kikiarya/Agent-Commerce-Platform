# syntax=docker/dockerfile:1
# Build one Spring Boot service: docker build --build-arg SERVICE=store -t ecommerce-store .

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY common ./common
COPY store ./store
COPY bank ./bank
COPY delivery ./delivery
COPY email ./email

RUN chmod +x gradlew

ARG SERVICE
RUN test -n "$SERVICE" \
  && ./gradlew ":${SERVICE}:bootJar" -x test --no-daemon \
  && find "${SERVICE}/build/libs" -name "*.jar" ! -name "*-plain.jar" -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:17-jre
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
