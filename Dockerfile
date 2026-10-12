# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 -g nogroup appuser
COPY --from=build /build/target/food-mobo-chain-1.0.0.jar /app/app.jar
ENV PORT=10000
# Optimize Java startup for the constrained Render Free CPU allocation; keep memory bounded.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8"
EXPOSE 10000
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
