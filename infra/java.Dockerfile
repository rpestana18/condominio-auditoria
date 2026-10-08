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
# Remove CRLF e garante a permissão de execução: num clone do Windows o gradlew
# pode chegar com CRLF ou sem o bit de execução, e aí o build para com código 127.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q :${SERVICO}:bootJar -x test \
    && cp ${SERVICO}/build/libs/${SERVICO}-*.jar /app.jar

FROM eclipse-temurin:25-jre
# /chaves: o volume nomeado do rag (chaves-rag) herda o dono da pasta da imagem; sem ela nasce do root e o rag
# não consegue gravar o par de chaves na primeira subida
RUN useradd --system --uid 1001 app && mkdir /dados /chaves && chown app /dados /chaves
USER app
COPY --from=build /app.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]
