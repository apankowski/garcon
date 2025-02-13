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

## Slash commands

The following [slash commands](https://api.slack.com/interactivity/slash-commands) are supported:

* `/lunch help` - displays short help message listing supported slash commands
* `/lunch` or `/lunch check` - manually triggers checking for lunch posts
* `/lunch log` - displays tail of the synchronization log

## Stack

The service is written in Kotlin and uses the following stack:

* Kotlin 2
* Gradle 8 (with build script in Kotlin)
* Spring Boot 3
* Jooq for database access
* PostgreSQL 10+
* Kotest 5 and MockK for tests
* ArchUnit 1 for architecture tests

## Building

Always use the Gradle wrapper (`./gradlew`) to build the project from command line.

Useful commands:

* `./gradlew build` - builds the project
* `./gradlew clean build` - fully rebuilds the project
* `./gradlew test` - runs all tests
* `./gradlew bootJar` - build & package the service as a fat JAR
* `./gradlew bootRun` - run the service locally (note: requires [configuration](#environment-variables))
* `./gradlew generateJooq` - (re)generate Jooq classes
* `./gradlew databaseUp` - run a local, empty, fully migrated PostgreSQL database (convenient for testing the service locally or running integration tests from IDE)
* `./gradlew databaseDown` - shut down local PostgreSQL database

During a build, a local, fully migrated PostgreSQL database is started and shut down after the build.

The service listens on HTTP port 8080 by default.

## Configuration

### Environment variables

#### Service

| Name                                | Description                                               | Required | Default/Example                           |
|-------------------------------------|-----------------------------------------------------------|:--------:|-------------------------------------------|
| `PORT`                              | HTTP port that will serve requests                        |    ✗     | `8080`                                    |
| `ACTUATOR_PORT`                     | HTTP port that will serve [Actuator endpoints](#actuator) |    ✗     | `8081`                                    |
| `JDBC_DATABASE_URL`                 | JDBC URL to the database                                  |    ✗     | `jdbc:postgresql://localhost:5432/garcon` |
| `JDBC_DATABASE_USERNAME`            | Username used to connect to the database                  |    ✗     | `garcon`                                  |
| `JDBC_DATABASE_PASSWORD`            | Password used to connect to the database                  |    ✗     | `garcon`                                  |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Structured logging format                                 |    ✗     | `ecs`, `gelf`, `logstash`; default: off   |

#### Application

| Name                                                                                    | Description                                                                                                                                                                                  | Required | Default/Example                                                                |
|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------:|--------------------------------------------------------------------------------|
| `LUNCH_SYNC_INTERVAL`                                                                   | Interval between consecutive synchronizations of lunch posts.                                                                                                                                |    ✗     | `PT5M`                                                                         |
| `LUNCH_CLIENT_USER_AGENT`                                                               | User agent by which the client identifies itself when fetching lunch pages.                                                                                                                  |    ✗     | `Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0` |
| `LUNCH_CLIENT_TIMEOUT`                                                                  | Max time to wait for the lunch page to be fetched (expressed as ISO 8601 time duration).                                                                                                     |    ✗     | `PT10S`                                                                        |
| `LUNCH_CLIENT_RETRY_COUNT`                                                              | Number of retries in case of failure.                                                                                                                                                        |    ✗     | `2`                                                                            |
| `LUNCH_CLIENT_RETRY_MIN_JITTER`                                                         | Min wait time between retries.                                                                                                                                                               |    ✗     | `PT0.05S`                                                                      |
| `LUNCH_CLIENT_RETRY_MAX_JITTER`                                                         | Max wait time between retries.                                                                                                                                                               |    ✗     | `PT3S`                                                                         |
| `LUNCH_PAGES_<INDEX>_KEY`, e.g. `LUNCH_PAGES_0_KEY`                                     | Textual key of the lunch page, used as fallback for the page name when reposting. Should not change once assigned.                                                                           |    ✓     | `PŻPS`                                                                         |
| `LUNCH_PAGES_<INDEX>_URL`, e.g. `LUNCH_PAGES_0_URL`                                     | URL of the lunch page.                                                                                                                                                                       |    ✓     | `https://www.facebook.com/1597565460485886/posts/`                             |
| `LUNCH_POST_LOCALE`                                                                     | Locale of text of posts used while extracting their keywords.                                                                                                                                |    ✗     | `Locale.ENGLISH`                                                               |
| `LUNCH_POST_KEYWORDS_<INDEX>_TEXT`, e.g. `LUNCH_POST_KEYWORDS_0_TEXT`                   | The keyword that makes a post be considered as a lunch post, e.g. `lunch` or `menu`.                                                                                                         |    ✗     | `lunch`                                                                        |
| `LUNCH_POST_KEYWORDS_<INDEX>_EDIT_DISTANCE`, e.g. `LUNCH_POST_KEYWORDS_0_EDIT_DISTANCE` | Maximum allowed [Damerau-Levenshtein distance](https://en.wikipedia.org/wiki/Damerau%E2%80%93Levenshtein_distance) between any word from a post and the lunch keyword. Typically `1` or `2`. |    ✗     | `1`                                                                            |
| `LUNCH_SLACK_SIGNING_SECRET`                                                            | Signing secret of the Slack app used for request verification. Request verification is disabled if the property is not set.                                                                  |    ✗     | `******`                                                                       |
| `LUNCH_SLACK_TOKEN`                                                                     | Token of the Slack app privileged to send and update reposts. Starts with `xoxb-`.                                                                                                           |    ✓     | `xoxb-some-token`                                                              |
| `LUNCH_SLACK_CHANNEL`                                                                   | Channel ID (`C1234567`) or name (`#random`) to send reposts to.                                                                                                                              |    ✓     | `#random`                                                                      |
| `LUNCH_REPOST_RETRY_INTERVAL`                                                           | Interval between consecutive attempts to retry failed reposts.                                                                                                                               |    ✗     | `PT10M`                                                                        |
| `LUNCH_REPOST_RETRY_BASE_DELAY`                                                         | Base delay in the exponential backoff between consecutive retries of a failed repost.                                                                                                        |    ✗     | `PT1M`                                                                         |
| `LUNCH_REPOST_RETRY_MAX_ATTEMPTS`                                                       | Max retry attempts for a failed repost.                                                                                                                                                      |    ✗     | `10`                                                                           |

## Installation

### Slack application

Create a Slack app if you don't have one already:

1. Go to [Slack Apps](https://api.slack.com/apps) → _Create New App_.
2. Pick a name & workspace to which the app should belong.
3. Configure additional stuff like description & icon.

Configure permissions and _Slash Commands_ for the app:

1. Go to [Slack Apps](https://api.slack.com/apps) → click on the name of your app.
2. Go to _Slash Commands_ (under _Features_ submenu) → _Create New Command_ → _Command_: `/lunch`, _Request URL_: `{BASE_URI}/commands/lunch` where `{BASE_URI}` is the base URI under which the bot is deployed/handles requests → _Save_.
3. Go to _OAuth & Permissions_ (under _Features_ submenu) → _Scopes_ section → _Bot Token Scopes_ subsection → _Add an OAuth Scope_ → select `chat:write` scope → confirm.
4. Go to _OAuth & Permissions_ (under _Features_ submenu) → _OAuth Tokens for Your Workspace_ section → Take note of the _Bot User OAuth Token_ (it starts with `xoxb-`). Set bot's `LUNCH_SLACK_TOKEN` [environment variable](#environment-variables) to this value.

Install the app:

1. Go to [Slack Apps](https://api.slack.com/apps) → click on the name of your app.
2. Go to _Install App_ (under _Settings_ submenu) → _Install to Workspace_.
3. In Slack, go to the channel in which lunch notifications are to be received. Type `/app` and select _Add apps to this channel_. Select the Slack application created above.

### PostgreSQL database

Create an empty PostgreSQL database for the bot with UTF-8 encoding to support emojis 😃. Take note of the credentials and make sure they allow DML & DDL queries as the service will automatically migrate the database schema.

### Docker image

1. As described in [Building & Running](#building) section create the fat JAR:
   ```
   ./gradlew bootJar
   ```
2. Build the docker image:
   ```
   docker build -t garcon .
   ```
3. Push built image to the docker registry of your choosing
4. Configure [environment variables](#environment-variables)
5. Deploy to the target environment

## Management & observability

### Actuator

Spring Boot [Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) are exposed under `/internal` prefix. By default, Actuator endpoints are available under a different port than the API - see `ACTUATOR_PORT` environment variable.

### Logging

By default, the service outputs logs to the console in a human-readable format.

To switch to structured logging, set `LOGGING_STRUCTURED_FORMAT_CONSOLE` environment variable to:

* `ecs` for Elastic Common Schema,
* `gelf` for Graylog Extended Log Format, or
* `logstash` for Logstash.

### Metrics

[Prometheus](https://prometheus.io/) scrape endpoint is exposed under `/internal/prometheus`. It provides [many metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics.supported) out of the box.

## Development

### Possible further work

* Slack configuration testing subcommand sending a test message
* Update/delete reposts based on upstream
* Custom business & technical metrics
* Adding verification of Slack request timestamps to prevent replay attacks
* Management / backoffice UI
* [Instagram](https://www.instagram.com/) support

### Checks

The repository contains definition of [pre-commit](https://pre-commit.com/) hooks in `.pre-commit-config.yaml`. After installation, before each commit, it automatically runs [Gitleaks](https://gitleaks.io/) on all staged changes.

To run these checks without making a commit:

* on staged files: `pre-commit run`,
* on all files: `pre-commit run -a`.
