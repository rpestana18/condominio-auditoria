# Compila o backend dentro do contêiner: ninguém precisa ter Java instalado para rodar
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY contracts contracts
COPY backend backend
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q :backend:app:bootJar -x test \
    && cp backend/app/build/libs/app-*.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app && mkdir /dados && chown app /dados
USER app
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
