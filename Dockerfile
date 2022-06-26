# OpenJDK builds are quite large at the moment:
# - openjdk:17-alpine is 181.71 MB compressed
# - openjdk:17-slim is 210.27 MB compressed
# Whereas:
# - azul/zulu-openjdk-alpine:17-jre-headless is 64.28 MB compressed
# - gcr.io/distroless/java17:latest is 81.7 MB compressed
FROM openjdk:17-alpine as build-jre

RUN jlink \
      --verbose \
      --compress 2 \
      --no-header-files \
      --no-man-pages \
      --output jre \
      --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.sql \
      # Required by Tomcat
      --add-modules java.security.jgss \
      # Required by Objenesis used by Spring AOP
      --add-modules jdk.unsupported \
      # Required for EC-based crypto used by TLS
      --add-modules jdk.crypto.ec \
      # Required by micrometer-core used by Spring Actuator
      --add-modules jdk.management

FROM alpine:latest as production

# Curl is used in healthcheck.
RUN apk --no-cache add curl

RUN addgroup -S nonroot && \
    adduser -S -H -G nonroot nonroot && \
    mkdir -p /app && \
    chown nonroot:nonroot /app
USER nonroot:nonroot

COPY --from=build-jre /jre /jre
ENV JAVA_HOME=/jre
ENV PATH="$PATH:$JAVA_HOME/bin"

COPY ./entrypoint.sh /app
COPY ./build/libs/application.jar /app
WORKDIR /app

EXPOSE 8080

HEALTHCHECK CMD curl --fail http://localhost:8080/internal/health || exit 1

# Heroku requires CMD to be specified. Can be changed to ENTRYPOINT when (if) we stop using Heroku.
#ENTRYPOINT ["/application/entrypoint.sh"]
CMD ["./entrypoint.sh"]
