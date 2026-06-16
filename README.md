# MoodLog

MoodLog is a private journaling web app that automatically classifies each entry's mood using AI and visualizes emotional trends over time. It is aimed at adults who want a low-friction way to track their mental state through free-text writing rather than checkboxes or forms.

## Tech Stack

- Java 21
- Spring Boot 4 (Spring MVC, Spring Security, Spring Data JPA, Spring AI)
- Thymeleaf (server-side templates)
- PostgreSQL + Flyway (production)
- H2 (local development)
- OpenAI-compatible API for mood classification

## Prerequisites

- Java 21
- Maven Wrapper is included — no separate Maven installation needed

## Running Locally

The `local` Spring profile uses an embedded H2 database and disables AI classification by default.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows:

```cmd
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on `http://localhost:8080`. Register an account, write journal entries, and review your mood history and trends.

> AI classification is disabled in the local profile (`moodlog.ai.enabled=false` in `application-local.properties`). Entries are saved with an unknown mood tag.

### Running with AI classification enabled

**Local LM Studio** (local inference at `http://localhost:1234`):

```cmd
set MOODLOG_AI_RESPONSE_FORMAT_TYPE=json_schema
set MOODLOG_AI_DEFAULT_MODEL=qwen2.5-7b-instruct-1m
set MOODLOG_AI_ENABLED=true
set MOODLOG_AI_PROVIDER=openai
set OPENAI_BASE_URL=http://localhost:1234/v1
set OPENAI_API_KEY=not-needed
set SPRING_AI_CHAT_MODEL=openai
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

**naga.ac** (remote OpenAI-compatible API):

```cmd
set MOODLOG_AI_RESPONSE_FORMAT_TYPE=json_schema
set MOODLOG_AI_DEFAULT_MODEL=llama-3.3-70b-instruct:free
set MOODLOG_AI_ENABLED=true
set MOODLOG_AI_PROVIDER=openai
set OPENAI_BASE_URL=https://api.naga.ac/v1
set OPENAI_API_KEY=ng-XXX
set SPRING_AI_CHAT_MODEL=openai
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Replace `ng-XXX` with your actual naga.ac API key.

## Environment Variables (Production)

| Variable | Description |
|---|---|
| `MOODLOG_DATABASE_URL` | JDBC URL for the PostgreSQL database |
| `MOODLOG_DATABASE_USERNAME` | Database username |
| `MOODLOG_DATABASE_PASSWORD` | Database password |
| `OPENAI_API_KEY` | API key for the OpenAI-compatible classifier |
| `OPENAI_BASE_URL` | Base URL for the AI API (default: `http://localhost:1234/v1`) |
| `MOODLOG_AI_ENABLED` | Enable AI classification (default: `true`) |
| `MOODLOG_AI_DEFAULT_MODEL` | Model name to use (default: `qwen2.5-7b-instruct-1m`) |

Optional overrides and their defaults are documented in `src/main/resources/application.properties`.

## Running Tests

```bash
./mvnw test
```
