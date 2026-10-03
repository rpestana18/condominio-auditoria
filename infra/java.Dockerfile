# Imagem de um serviço Java (backend, rag ou mcp). O serviço vem no argumento SERVICO.
# Compila dentro do contêiner: ninguém precisa ter Java instalado para rodar.
FROM eclipse-temurin:25-jdk AS build
ARG SERVICO
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY contracts contracts
COPY libs libs
COPY backend backend
COPY rag rag
COPY mcp mcp
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q :${SERVICO}:bootJar -x test \
    && cp ${SERVICO}/build/libs/${SERVICO}-*.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app && mkdir /dados && chown app /dados
USER app
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]
