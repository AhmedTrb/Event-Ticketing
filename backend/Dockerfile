# syntax=docker/dockerfile:1.7
#
# One lightweight, parametrised image recipe for every Spring Boot module.
#   docker build --build-arg MODULE=ticketing-api -t ticketing-api .
#
# Build stage : full Maven + JDK (discarded afterwards).
# Runtime     : plain Alpine + jlink-trimmed JRE, Spring Boot layered jar
#               (dependencies live in their own cacheable layer), non-root
#               user, container-aware JVM.

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
ARG JDK_IMAGE=eclipse-temurin:17-jdk-alpine
ARG BASE_IMAGE=alpine:3.21

# ---------------------------------------------------------------- build
FROM ${MAVEN_IMAGE} AS build
ARG MODULE
WORKDIR /build

# Poms first so source edits don't bust the dependency cache
COPY pom.xml .
COPY shared/pom.xml shared/
COPY ticketing-api/pom.xml ticketing-api/
COPY notification-service/pom.xml notification-service/
COPY registration-worker/pom.xml registration-worker/

COPY shared/src shared/src
COPY ${MODULE}/src ${MODULE}/src

# Tests run separately (see tests/README.md); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -Dasciidoctor.skip=true -pl ${MODULE} -am package \
 && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*.jar extract --layers --launcher --destination /layers

# ---------------------------------------------------------------- custom JRE
# A trimmed runtime (only the JDK modules Spring Boot + Hibernate + Tomcat +
# AMQP + Redis + Keycloak client need) built with jlink on musl, so it runs on
# plain Alpine. ~40% smaller images than the stock JRE image.
FROM ${JDK_IMAGE} AS jre
ARG JRE_MODULES=java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.net,jdk.unsupported,jdk.zipfs
RUN apk add --no-cache binutils \
 && jlink --add-modules ${JRE_MODULES} --strip-debug --no-man-pages --no-header-files \
          --compress=2 --output /opt/jre

# ---------------------------------------------------------------- runtime
FROM ${BASE_IMAGE}
RUN addgroup -S app && adduser -S -G app app
COPY --from=jre /opt/jre /opt/jre
ENV PATH="/opt/jre/bin:${PATH}"
WORKDIR /app

# Least-changing layers first => best layer cache reuse on rebuilds
COPY --from=build --chown=app:app /layers/dependencies/ ./
COPY --from=build --chown=app:app /layers/spring-boot-loader/ ./
COPY --from=build --chown=app:app /layers/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /layers/application/ ./

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
