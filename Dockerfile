FROM docker.io/library/eclipse-temurin:17.0.20_8-jdk AS build
WORKDIR /src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
 && ./mvnw -q dependency:go-offline
# 위: pom만 먼저 복사해 의존성 레이어를 캐시, CRLF 제거와 실행 권한 보장
COPY schema.sql ./
COPY src src
RUN ./mvnw -q -DskipTests package

FROM docker.io/library/eclipse-temurin:17.0.20_8-jre
RUN useradd --system --no-create-home app
USER app
# 위: root가 아닌 일반 사용자로 실행
COPY --from=build /src/target/remittance-0.0.1-SNAPSHOT.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
