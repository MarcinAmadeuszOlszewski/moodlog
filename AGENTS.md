# Repository Guidelines

MoodLog is a Java 21 / Spring Boot 4 web app with Thymeleaf views and a small HTTP+HTML flow rooted in `@src/main/java/com/amadeuszx/moodlog` and `@src/main/resources/templates/index.html`. Product scope lives in `@context/foundation/prd.md`; broader workflow context lives in `@.github/copilot-instructions.md`.

## Hard rules

- Keep the package root `com.amadeuszx.moodlog`. Put new controllers next to `@src/main/java/com/amadeuszx/moodlog/IndexController.java` and `@src/main/java/com/amadeuszx/moodlog/RandomNumberController.java`; put business logic next to `@src/main/java/com/amadeuszx/moodlog/RandomNumberService.java`.
- In production Java, declare local variables with explicit types and mark them final unless the variable is reassigned later in the same method. Do not use var or Lombok val.
- In test classes, method names must stay camelCase and must not contain underscores.
- In test classes, every test method needs `@DisplayName` for the detailed scenario description.
- In test classes, declare local variables with Lombok `val` instead of explicit types or `var`; follow the pattern `val result = service.process(input);`.
- Order class-level annotations as Spring annotations first, Lombok annotations second, and other annotations last; keep alphabetical order inside each group.
- Preserve Polish UI copy and UTF-8 behavior in `@src/main/resources/templates/index.html` and `@src/main/resources/application.properties`. If endpoint text changes, update the MVC test expectations.
- Whenever possible use Lombok annotations to minimize boilerplate (especially for constructors, getters, and builders, logger declarations)

## Project structure & commands

- Main code lives in `@src/main/java/com/amadeuszx/moodlog`; templates live in `@src/main/resources/templates`; app settings live in `@src/main/resources/application.properties`.
- Tests live in `@src/test/java/com/amadeuszx/moodlog` and currently follow the single integration-style pattern in `@src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`.
- Use the Maven Wrapper from `@mvnw` / `@mvnw.cmd`: `./mvnw test` (Windows: `.\mvnw.cmd test`), `./mvnw spring-boot:run`, and `./mvnw package`.

## Coding style & testing

- Match the existing tab-indented Java formatting in `@src/main/java/com/amadeuszx/moodlog/Application.java` and `@src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`.
- Prefer `@SpringBootTest` plus `MockMvc` when a change affects routing, rendered HTML, or response encoding.
- For the current feature set, assert both the `/v1/random` contract and the rendered welcome text on `/`.
- There is no README, CI workflow, or git history yet. Do not invent commit conventions or CI gates; if you add them, update this file in the same change.
