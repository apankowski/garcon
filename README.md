# Garçon

Simple PoC ("toy" if you will) of a [Slack](https://slack.com/) bot that re-posts lunch offers from chosen Facebook pages.

It doesn't use the [Graph API](https://developers.facebook.com/docs/graph-api/) to get the offers as the Graph API doesn't allow accessing content of Facebook pages willy-nilly and that is exactly what I want to do. Instead, it extracts necessary data directly from Facebook page's DOM.

After extraction comes classification. To say whether a post is a lunch post or not the bot breaks it into a collection of words and searches for one of predefined keywords. To handle typos, misspellings, etc. the words are matched against keywords using [Damerau-Levenshtein distance](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance).

Each lunch post is then reposted to Slack using its [API](https://api.slack.com/).

Fetched posts along with their classification, repost status, etc. are saved in a database to prevent same lunch offers from being reposted multiple times as well as allow the bot to be restarted without loosing data.

The whole procedure is then repeated in regular intervals.

⚠️

I do not endorse scraping Facebook pages. Again: this is a PoC.

To protect certain things the original git commit history had to be wiped out.

## Stack

The service is written in Kotlin and uses the following stack:

  * Kotlin 1.4 on Java 15
  * Gradle 6 (with build script in Kotlin)
  * Spring Boot 2
  * Spock for tests (written in Groovy)
  * Jooq for database access
  * PostgreSQL

## Building & running locally

Always use the Gradle wrapper (`./gradlew`) to build the project from command line.

Useful commands:

  * `./gradlew build` - builds the project
  * `./gradlew clean build` - fully rebuilds the project
  * `./gradlew test` - runs all tests
  * `./gradlew bootJar` - build & package the service as a fat JAR
  * `./gradlew bootRun` - build & run the service locally
  * `./gradlew jooq-codegen-database` - (re)generate Jooq classes
  * `./gradlew databaseUp` - run a local, empty, fully migrated PostgreSQL database (convenient for testing the service locally or running integration tests from IDE)
  * `./gradlew composeDown` - shut down PostgreSQL database

During a build, a local, fully migrated PostgreSQL database is started and shut down after the build.

The service listens on HTTP port 8080 by default.

## Installation

### Slack application

Create a Slack app if you don't have one already:

 1. Go to [Slack Apps](https://api.slack.com/apps) → _Create New App_.
 1. Pick a name & workspace to which the app should belong.
 1. Configure additional stuff like description & icon.

Configure _Incoming Webhooks_ and _Slash Commands_ for the app:

 1. Go to [Slack Apps](https://api.slack.com/apps) → click on the name of your app.
 1. Go to _Incoming Webhooks_ (under _Features_ submenu) → _Add New Webhook to Workspace_ → select channel to which lunch posts will be reposted → _Allow_ → take note of the _Webhook URL_.
 1. Go to _Slash Commands_ (under _Features_ submenu) → _Create New Command_ → _Command_: `/lunch`, _Request URL_: `{BASE_URI}/commands/lunch` where `{BASE_URI}` is the base URI under which the bot will be deployed/will handle requests → _Save_.

### Docker image

 1. As described in [Building & Running](#BuildingRunning) section create the fat JAR:

    ```
    ./gradlew bootJar
    ```

 1. Build the docker image:

    ```
    docker build -t garcon .
    ```

 1. Push built image to the docker registry of your choosing & deploy to your target environment. If you're going with Heroku, see the [respective section](#Heroku).

### PostgreSQL database

Create an empty PostgreSQL database for the bot with UTF-8 encoding to support emojis 😃. Take note of the credentials and make sure they allow DML & DDL queries as the bot will automatically migrate the database schema.

### Environment variables

| Name | Description | Required | Default/Example |
|------|-------------|:--------:|-----------------|
| `PORT` | HTTP port that will serve requests | ✓ | `8080` |
| `JDBC_DATABASE_URL` | JDBC URL to the database | ✗ | `jdbc:postgresql://localhost:5432/garcon` |
| `JDBC_DATABASE_USERNAME` | Username used to connect to the database | ✗ | `garcon` |
| `JDBC_DATABASE_PASSWORD` | Password used to connect to the database | ✗ | `garcon` |
| `LUNCH_SLACK_WEBHOOK_URL` | URL of Slack's Incoming Webhook that will be used to send lunch messages. | ✗ | `https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX` |
| `LUNCH_SYNC_INTERVAL` | Interval between consecutive checks for lunch posts. | ✗ | `PT5M` |
| `LUNCH_CLIENT_USER_AGENT` | User agent by which the client identifies itself when fetching lunch pages. | ✗ | `Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0` |
| `LUNCH_CLIENT_TIMEOUT` | Max time to wait for the lunch page to be fetched (expressed as ISO 8601 time duration). | ✗ | `PT10S` |
| `LUNCH_PAGES_<INDEX>_ID`, e.g. `LUNCH_PAGES_0_ID` | Textual identifier of the lunch page presented as the name of the page when reposting. Should not change once assigned. | ✓ | `PŻPS` |
| `LUNCH_PAGES_<INDEX>_URL`, e.g. `LUNCH_PAGES_0_URL` | URL of the lunch page. | ✓ | `https://www.facebook.com/1597565460485886/posts/` |

### Heroku

The service can be deployed to Heroku. The following changes were made to meet requirements imposed by Heroku:

 1. `Dockerfile` declares a `CMD` - see [this question & answers](https://stackoverflow.com/q/55913408/1820695).
 1. Service is configured to bind to HTTP port provided via `PORT` environment variable - [see here](https://devcenter.heroku.com/articles/dynos#web-dynos).
 1. If `DATABASE_URL` environment variable is set, its value is assumed to be a JDBC URL and split into the following environment variables: `JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME` & `JDBC_DATABASE_PASSWORD`. See [here](https://devcenter.heroku.com/articles/connecting-to-relational-databases-on-heroku-with-java) for more information.

To deploy the service issue the following commands (presence of `heroku` CLI and deployment-level access to the service is assumed):

 1. `heroku login`
 1. `heroku container:login`
 1. `heroku container:push web --app garcon`
 1. `heroku container:release web --app garcon`

See [here](https://devcenter.heroku.com/articles/container-registry-and-runtime) for more details.

## Actuator

The service exposes [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/actuator-api/html/) endpoints under `/internal` prefix.

Specifically, information about the service & its health can be observed via the following endpoints:

  * `/internal/info` - information about the bot, e.g. version, build commit hash, etc.
  * `/internal/health` - health of the bot and its dependencies, e.g. usage of disk space, ability to reach database, etc.

## Possible further work

  * Slack webhook testing subcommand
  * Fetch page name from metadata
  * Configurable lunch keywords
  * Retry failed reposts
  * Update/delete reposts based on upstream
  * [Prometheus](https://prometheus.io/) metrics
  * [Instagram](https://www.instagram.com/) support
