FROM openjdk:11-jre-slim

# Curl is used in healthcheck.
RUN apt-get update && apt-get install -y curl

RUN mkdir -p /application
COPY ./entrypoint.sh /application
COPY ./build/libs/application.jar /application

WORKDIR /application

EXPOSE 8080

HEALTHCHECK CMD curl --fail http://localhost:8080/internal/health || exit 1

# Heroku requires CMD to be specified. Can be changed to ENTRYPOINT when (if) we stop using Heroku.
#ENTRYPOINT ["/application/entrypoint.sh"]
CMD ["/application/entrypoint.sh"]
