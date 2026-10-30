FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /app/logs && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 /build/target/movie-booking-system-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
