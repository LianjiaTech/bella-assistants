# Repository Guidelines

## Project Structure & Module Organization
- Source: `src/main/java/com/ke/assistant/` (e.g., `controller`, `service`, `configuration`, `model`, `util`, `db`). Entrypoint: `src/main/java/com/ke/assistant/Application.java`.
- Resources: `src/main/resources` (Spring config, templates, static assets).
- Tests: `src/test/java/com/ke/assistant/`; test assets in `src/test/resources`.
- Codegen: `src/codegen/java` (JOOQ generated classes); SQL in `sql/`.
- Build & config: `pom.xml`; containerization: `Dockerfile`; local scripts in `bin/`.
- API docs (dev): `/swagger-ui/index.html` or `/swagger-ui.html` when running locally.

## Architecture Overview
- Spring Boot API with layered design: controller → service → db.
- JOOQ for type-safe SQL; Redis via Redisson; OpenAPI docs with springdoc.

## Build, Test, and Development Commands
- `mvn clean package` — builds the jar and runs tests.
- `mvn test` — runs the test suite.
- `mvn spring-boot:run` — runs the app locally.
- `mvn org.jooq:jooq-codegen-maven:generate` — generates JOOQ classes (uses DB settings in `pom.xml`).
- Run packaged app: `java -jar target/*.jar`.
- `sh bin/build.sh [-Dmaven.test.skip=true]` — builds `release/` for Docker image packaging.
- Docker: `docker build -t bella-assistant .` then `docker run -p8080:8080 bella-assistant`.

## Coding Style & Naming Conventions
- Java8 (source/target=1.8); use4-space indentation.
- Packages: lower case (`com.ke.assistant.*`). Classes: PascalCase; methods/fields: lowerCamelCase; constants: UPPER_SNAKE_CASE.
- Suffixes: controllers `*Controller`, services `*Service`, configuration `*Configuration`/`*Config`.
- Prefer Lombok (`@Slf4j`, `@Getter/@Setter`) over manual boilerplate.

## Testing Guidelines
- Framework: Spring Boot Starter Test (JUnit Jupiter).
- Unit tests in `src/test/java`; name files `*Test.java` (e.g., `UserServiceTest`).
- Use `@SpringBootTest` for integration; `@WebMvcTest` for controller slices.
- Aim for meaningful coverage of services/controllers; include negative paths.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`; format `type(scope): summary`.
- Keep PRs focused; avoid committing secrets.

## Security & Configuration Tips
- Configure DB/Redis via environment variables or Spring profiles (`application-*.yml`); do not commit secrets.
- JOOQ codegen reads `db.*` properties from `pom.xml`; verify credentials locally before generating.
