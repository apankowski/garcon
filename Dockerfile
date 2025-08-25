# The dockerfile mixes common best practices with tips from https://spring.io/guides/topicals/spring-boot-docker
# It could be taken a step further by getting rid of JarLauncher, but let's not get ahead of ourselves

# eclipse-temurin:*-jre-alpine base images are only 65 MB compressed
FROM eclipse-temurin:21.0.8_9-jre-alpine AS builder

WORKDIR /workspace

# Extract layers of the fat JAR
COPY ./build/libs/application.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract --destination extracted

FROM eclipse-temurin:21.0.8_9-jre-alpine AS production

RUN \
    # Install curl used in the healthcheck
    apk --no-cache add 'curl>=7.80.0'

# Switch to non-root
RUN addgroup -S nonroot && \
    adduser -S -H -G nonroot nonroot && \
    mkdir -p /app && \
    chown nonroot:nonroot /app
USER nonroot:nonroot

WORKDIR /app

# Copy over extracted layers, changing ownership of files to our non-root user
ARG EXTRACTED=/workspace/extracted
COPY --from=builder --chown=nonroot:nonroot ${EXTRACTED}/dependencies/ ./
COPY --from=builder --chown=nonroot:nonroot ${EXTRACTED}/spring-boot-loader/ ./
COPY --from=builder --chown=nonroot:nonroot ${EXTRACTED}/snapshot-dependencies/ ./
COPY --from=builder --chown=nonroot:nonroot ${EXTRACTED}/application/ ./

EXPOSE 8080

HEALTHCHECK CMD curl --fail http://localhost:8080/internal/health || exit 1

ENTRYPOINT [ \
  "java", \
  "-Dorg.jooq.no-logo=true", \
  "-Dorg.jooq.no-tips=true", \
  "org.springframework.boot.loader.launch.JarLauncher" \
]
