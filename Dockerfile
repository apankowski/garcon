# OpenJDK images are quite large:
# - openjdk:19-alpine is 185.34 MB compressed
# - openjdk:19-slim is 218.92 MB MB compressed
# Whereas:
# - azul/zulu-openjdk-alpine:19-jre-headless is 66.13 MB compressed
FROM azul/zulu-openjdk-alpine:19-jre-headless as production

# Curl is used in healthcheck.
RUN apk --no-cache add curl

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

# Heroku requires CMD to be specified. Can be changed to ENTRYPOINT when (if) we stop using Heroku.
#ENTRYPOINT ["/application/entrypoint.sh"]
CMD ["./entrypoint.sh"]
