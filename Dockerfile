FROM docker.io/library/eclipse-temurin:17.0.20_8-jdk AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && ./mvnw -q dependency:go-offline
# above: copy only the pom first to cache the dependency layer; strip CRLF and ensure the execute bit
COPY schema.sql ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM docker.io/library/eclipse-temurin:17.0.20_8-jre
RUN useradd --system --no-create-home app
USER app
# above: run as a non-root user
COPY --from=build /src/target/remittance-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
