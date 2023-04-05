<div align="center">
<img src="./assets/logo.png" alt="Logo" style="width: 300px" />
<h1>Garçon</h1>
<a href="https://sonarcloud.io/summary/new_code?id=garcon"><img src="https://sonarcloud.io/api/project_badges/measure?project=garcon&metric=ncloc" alt="Lines of Code" /></a>
<a href="https://sonarcloud.io/summary/new_code?id=garcon"><img src="https://sonarcloud.io/api/project_badges/measure?project=garcon&metric=coverage" alt="Coverage" /></a>
<a href="https://codescene.io/projects/22033"><img src="https://codescene.io/projects/22033/status-badges/code-health" alt="Code Health" /></a>

Bot re-posting lunch posts from chosen Facebook pages on Slack.
<br />
<br />
</div>

## How does it work?

The bot doesn't use Facebook's [Graph API](https://developers.facebook.com/docs/graph-api/) to get the posts as the Graph API doesn't allow accessing content of Facebook pages willy-nilly and that is exactly what we want to do. Instead, it scrapes necessary data directly from Facebook pages.

After extraction comes classification. To say whether a post is a lunch post or not the bot breaks it into a collection of words and searches for predefined keywords. To handle typos, misspellings, etc. the words are matched against keywords using [Damerau-Levenshtein distance](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance).

Each lunch post is then reposted to Slack using its [API](https://api.slack.com/).

Fetched posts along with their classification, repost status, etc. are saved in a database to prevent same lunch offers from being reposted multiple times as well as allow the bot to be restarted without loosing data.

The whole procedure is repeated in regular intervals.

⚠️ I do not endorse scraping Facebook pages.

## Stack

The service is written in Kotlin and uses the following stack:

* Kotlin 1.8 on Java 19 (docker image is based on JRE 20)
* Gradle 8.0 (with build script in Kotlin)
* Spring Boot 3.0
* Jooq for database access
* PostgreSQL 10+
* Kotest 5.5 and MockK 1.13 for tests
* ArchUnit 1.0 for architecture tests

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
* `./gradlew databaseDown` - shut down local PostgreSQL database

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

1. As described in [Building & Running](#building--running-locally) section create the fat JAR:

   ```
   ./gradlew bootJar
   ```

1. Build the docker image:

   ```
   docker build -t garcon .
   ```

1. Push built image to the docker registry of your choosing & deploy to your target environment.

### PostgreSQL database

Create an empty PostgreSQL database for the bot with UTF-8 encoding to support emojis 😃. Take note of the credentials and make sure they allow DML & DDL queries as the bot will automatically migrate the database schema.

### Environment variables

| Name | Description | Required | Default/Example |
|------|-------------|:--------:|-----------------|
| `PORT` | HTTP port that will serve requests | ✗ | `8080` |
| `ACTUATOR_PORT` | HTTP port that will serve Actuator endpoints | ✗ | `8081` |
| `JDBC_DATABASE_URL` | JDBC URL to the database | ✗ | `jdbc:postgresql://localhost:5432/garcon` |
| `JDBC_DATABASE_USERNAME` | Username used to connect to the database | ✗ | `garcon` |
| `JDBC_DATABASE_PASSWORD` | Password used to connect to the database | ✗ | `garcon` |
| `LUNCH_SYNC_INTERVAL` | Interval between consecutive synchronizations of lunch posts. | ✗ | `PT5M` |
| `LUNCH_CLIENT_USER_AGENT` | User agent by which the client identifies itself when fetching lunch pages. | ✗ | `Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0` |
| `LUNCH_CLIENT_TIMEOUT` | Max time to wait for the lunch page to be fetched (expressed as ISO 8601 time duration). | ✗ | `PT10S` |
| `LUNCH_CLIENT_RETRY_COUNT` | Number of retries in case of failure. | ✗ | `2` |
| `LUNCH_CLIENT_RETRY_MIN_JITTER` | Min wait time between retries. | ✗ | `PT0.05S` |
| `LUNCH_CLIENT_RETRY_MAX_JITTER` | Max wait time between retries. | ✗ | `PT3S` |
| `LUNCH_PAGES_<INDEX>_ID`, e.g. `LUNCH_PAGES_0_ID` | Textual identifier of the lunch page presented as the name of the page when reposting. Should not change once assigned. | ✓ | `PŻPS` |
| `LUNCH_PAGES_<INDEX>_URL`, e.g. `LUNCH_PAGES_0_URL` | URL of the lunch page. | ✓ | `https://www.facebook.com/1597565460485886/posts/` |
| `LUNCH_POST_LOCALE` | Locale of text of posts used while extracting their keywords. | ✗ | `Locale.ENGLISH` |
| `LUNCH_POST_KEYWORDS_<INDEX>_TEXT`, e.g. `LUNCH_POST_KEYWORDS_0_TEXT` | The keyword that makes a post be considered as a lunch post, e.g. `lunch` or `menu`. | ✗ | `lunch` |
| `LUNCH_POST_KEYWORDS_<INDEX>_EDIT_DISTANCE`, e.g. `LUNCH_POST_KEYWORDS_0_EDIT_DISTANCE` | Maximum allowed [Damerau-Levenshtein distance](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance) between any word from a post and the lunch keyword. Typically `1` or `2`. | ✗ | `1` |
| `LUNCH_SLACK_WEBHOOK_URL` | URL of Slack's Incoming Webhook that will be used to repost lunch offers. | ✓ | `https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX` |
| `LUNCH_SLACK_SIGNING_SECRET` | Signing secret of the Slack app used for request verification. Request verification is disabled if the property is not set. | ✗ | `******` |
| `LUNCH_REPOST_RETRY_INTERVAL` | Interval between consecutive attempts to retry failed reposts. | ✗ | `PT10M` |
| `LUNCH_REPOST_RETRY_BASE_DELAY` | Base delay in the exponential backoff between consecutive retries of a failed repost. | ✗ | `PT1M` |
| `LUNCH_REPOST_RETRY_MAX_ATTEMPTS` | Max retry attempts for a failed repost. | ✗ | `10` |

## Actuator

The service exposes [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/actuator-api/html/) endpoints under `/internal` prefix. By default, Actuator endpoints are available under a different port than the API - see `ACTUATOR_PORT` environment variable.

## Metrics

The service exposes [many metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.supported) under a [Prometheus](https://prometheus.io/) scrape endpoint: `/internal/prometheus`.

## Possible further work

* Slack webhook testing subcommand
* Update/delete reposts based on upstream
* Custom business & technical metrics
* Adding verification of Slack request timestamps to prevent replay attacks
* Management / backoffice UI
* [Instagram](https://www.instagram.com/) support
