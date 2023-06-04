# OpenJDK images are quite large:
# - openjdk:20-alpine is 189.42 MB compressed
# - openjdk:20-slim is 220.77 MB compressed
# Whereas:
# - azul/zulu-openjdk-alpine:20-jre-headless is 66.64 MB compressed
FROM azul/zulu-openjdk-alpine:20-jre-headless as production

RUN \
    # Install curl used in the healthcheck
    apk --no-cache add 'curl>=7.80.0' && \
    # Update libssl and libcrypto defending against CVE-2023-0465
    # Remove once default version in base image is >= 1.1.1t-r3
    apk --no-cache add --upgrade 'libssl1.1>=1.1.1u-r1' 'libcrypto1.1>=1.1.1u-r1'

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
