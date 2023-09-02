# OpenJDK images are quite large:
# - openjdk:20-alpine is 189.42 MB compressed
# - openjdk:20-slim is 220.77 MB compressed
# Whereas:
# - azul/zulu-openjdk-alpine:20.0.2-jre-headless is 67.2 MB MB compressed
FROM azul/zulu-openjdk-alpine:20.0.2-jre-headless as production

RUN \
    # Install curl used in the healthcheck
    apk --no-cache add 'curl'

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
