# Repository Guidelines

MoodLog is a Java 21 / Spring Boot 4 web app with Thymeleaf views and a small HTTP+HTML flow rooted in `@src/main/java/com/amadeuszx/moodlog` and `@src/main/resources/templates/index.html`. Product scope lives in `@context/foundation/prd.md`; broader workflow context lives in `@.github/copilot-instructions.md`.

## Hard rules

- Keep the package root `com.amadeuszx.moodlog`. Put new controllers next to `@src/main/java/com/amadeuszx/moodlog/landing/IndexController.java`; put business logic in the relevant feature package under `com.amadeuszx.moodlog`.
- In production Java, declare local variables with explicit types and mark them final unless the variable is reassigned later in the same method. Do not use var or Lombok val.
- In test classes, method names must stay camelCase and must not contain underscores.
- In test classes, every test method needs `@DisplayName` for the detailed scenario description.
- In test classes, declare local variables with Lombok `val` instead of explicit types or `var`; follow the pattern `val result = service.process(input);`.
- Order class-level annotations as Spring annotations first, Lombok annotations second, and other annotations last; keep alphabetical order inside each group.
- Preserve Polish UI copy and UTF-8 behavior in `@src/main/resources/templates/index.html` and `@src/main/resources/application.properties`. If template text changes, update the MVC test expectations in `ApplicationTests`.
- Whenever possible use Lombok annotations to minimize boilerplate (especially for constructors, getters, and builders, logger declarations)

## Project structure & commands

- Main code lives in `@src/main/java/com/amadeuszx/moodlog`; templates live in `@src/main/resources/templates`; app settings live in `@src/main/resources/application.properties`.
- Tests live in `@src/test/java/com/amadeuszx/moodlog` and currently follow the single integration-style pattern in `@src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`.
- Use the Maven Wrapper from `@mvnw` / `@mvnw.cmd`: `./mvnw test` (Windows: `.\mvnw.cmd test`), `./mvnw spring-boot:run`, and `./mvnw package`.

## Coding style & testing

- Match the existing tab-indented Java formatting in `@src/main/java/com/amadeuszx/moodlog/Application.java` and `@src/test/java/com/amadeuszx/moodlog/ApplicationTests.java`.
- Prefer `@SpringBootTest` plus `MockMvc` when a change affects routing, rendered HTML, or response encoding.
- When a public route or landing page copy changes, update the assertions in `ApplicationTests` to match the new content.
