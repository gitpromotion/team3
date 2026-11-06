# ===== 1단계: 빌드 (Maven + JDK 17) =====
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests
# 빌드 결과 확인 (실패 시 로그에서 바로 보이게)
RUN ls -l /app/target/app.jar

# ===== 2단계: 실행 (경량 JRE) =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/app.jar /app/app.jar
EXPOSE 8080
# server.port=${PORT} 는 application.properties 가 처리 (Render의 PORT 사용)
ENTRYPOINT ["java","-jar","/app/app.jar"]
