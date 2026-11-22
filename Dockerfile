# ---------- Estágio 1: BUILD ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copia PRIMEIRO só o que define as dependências (pom.xml e o Maven Wrapper).
# O Docker guarda cada passo em cache: enquanto o pom.xml não mudar, as dependências
# NÃO são baixadas de novo, e o build fica muito mais rápido.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline

# Agora copia o código e gera o .jar (os testes rodam na esteira de CI, antes do build)
COPY src src
RUN ./mvnw -q package -DskipTests

# ---------- Estágio 2: RUNTIME ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl: usado pelo HEALTHCHECK abaixo. "--no-install-recommends" e a limpeza do cache
# do apt mantêm a imagem pequena.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Segurança: não rodar a aplicação como root dentro do container
RUN useradd --system --no-create-home spring
USER spring

# Pega SÓ o .jar gerado no estágio de build
COPY --from=build /app/target/*.jar app.jar

# Documenta a porta que a aplicação usa (quem publica a porta é o compose)
EXPOSE 8080

# Memória da JVM: usa até 75% da memória que o CONTAINER recebeu.
# (Sem isso, a JVM pode tentar usar mais do que o limite do container e ser morta pelo Docker.)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# O Docker chama este endereço periodicamente. Se falhar várias vezes, marca o container
# como "unhealthy" (e orquestradores como Docker Swarm/Kubernetes o reiniciam).
# start-period: tempo de tolerância enquanto a aplicação ainda está subindo.
#
# Por que /liveness e não /actuator/health?
# O /actuator/health checa TUDO, inclusive o servidor de email e o banco. Se o provedor de
# email ficar instável, a aplicação seria marcada como "doente" e reiniciada à toa,
# sendo que reiniciar não resolve o problema do email. O "liveness" responde só:
# "o processo da aplicação está vivo e funcionando?"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
