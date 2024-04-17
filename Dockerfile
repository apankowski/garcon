# OpenJDK image is large: openjdk:21-slim is 226.44 MB MB compressed
# Whereas: azul/zulu-openjdk-alpine:21.0.1-jre-headless is 68.75 MB compressed
# Another contender: eclipse-temurin:21-jre-alpine is 66.43 MB compressed
FROM azul/zulu-openjdk-alpine:21.0.3-jre-headless as production

RUN \
    # Install curl used in the healthcheck
    apk --no-cache add 'curl>=7.80.0'

RUN addgroup -S nonroot && \
    adduser -S -H -G nonroot nonroot && \
    mkdir -p /app && \
    chown nonroot:nonroot /app
USER nonroot:nonroot

COPY ./entrypoint.sh /app
COPY ./build/libs/application.jar /app
WORKDIR /app

EXPOSE 8080

HEALTHCHECK CMD curl --fail http://localhost:8080/internal/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
