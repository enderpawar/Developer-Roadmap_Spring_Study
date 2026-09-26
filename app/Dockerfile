# Day31 — 멀티스테이지 빌드. 1단계(빌드)에는 JDK+Gradle이 다 들어있어서 무겁고,
# 2단계(실행)에는 컴파일된 jar 하나만 옮겨서 최종 이미지를 가볍게 만든다.
#
# 공식 gradle 이미지 태그가 이 프로젝트의 gradle-wrapper.properties 버전(8.14.5)과 정확히
# 일치하지 않을 수 있어서, JDK 이미지 위에서 프로젝트 자체 gradlew(wrapper)를 그대로 쓴다 —
# wrapper가 알아서 정확한 gradle 버전을 내려받아 빌드해준다.

# ---- 1단계: 빌드 ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew
# 이미지 안에서는 테스트를 또 돌릴 필요가 없다 — CI(Day32)가 이미 test 잡에서 검증한다.
RUN ./gradlew bootJar --no-daemon -x test

# ---- 2단계: 실행 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# Day29에서 만든 프로필 분리 — 컨테이너로 뜰 때는 항상 docker 프로필(application-docker.yml)을 쓴다.
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
