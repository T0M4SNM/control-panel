# Imagen del backend. La construye GitHub Actions (.github/workflows/release.yml); el servidor solo
# la descarga. Los tests ya se ejecutan en la CI, por eso aquí se empaqueta con -DskipTests.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Las dependencias van en su propia capa: si solo cambia el código, no se vuelven a descargar
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar /src/app.jar

FROM eclipse-temurin:21-jre

# Las comprobaciones PING usan ICMP, que necesita sockets «raw». En vez de ejecutar como root, se
# da solo ese permiso al binario de Java. Con capacidades, Linux ignora las rutas relativas de las
# librerías nativas, así que se registra la carpeta de libjli.so en ldconfig.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libcap2-bin \
    && rm -rf /var/lib/apt/lists/* \
    && setcap cap_net_raw+ep "$JAVA_HOME/bin/java" \
    && echo "$JAVA_HOME/lib" > /etc/ld.so.conf.d/java.conf \
    && echo "$JAVA_HOME/lib/server" >> /etc/ld.so.conf.d/java.conf \
    && ldconfig \
    && useradd --system --uid 10001 --no-create-home app

WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app

# La JVM usa como mucho el 75 % de la memoria que le dé Docker (mem_limit en el compose)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
