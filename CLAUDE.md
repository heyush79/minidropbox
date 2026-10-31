# minidropbox

## What this is
- Small Dropbox-style REST API: register/login with JWT, upload/download/list/soft-delete files on local disk, share/revoke files with other users, chunked (multi-part) uploads, audit log.
- Java 17 (`<java.version>17`), Spring Boot 4.0.2 (Spring MVC, Security, Data JPA / Hibernate 7, Tomcat 11), PostgreSQL, Lombok, jjwt 0.11.5, springdoc 2.5.0.
- No README, Dockerfile or CI config in the repo.

## How to run it
- Use `sh mvnw ...`: `mvnw` is committed without the executable bit, so `./mvnw` fails with "permission denied". The wrapper downloads Maven 3.9.12.
- Compile: `sh mvnw -B compile` (~75s on first run while it downloads dependencies, a few seconds after that).
- Package: `sh mvnw -B package -DskipTests` (~4s warm) -> `target/minidropbox-0.0.1-SNAPSHOT.jar`.
- Run: `sh mvnw spring-boot:run` or `java -jar target/minidropbox-0.0.1-SNAPSHOT.jar`. Default port 8080. To use another port: `sh mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18081`.
- Test, whole suite: `sh mvnw -B test` (~7s). Fails unless Postgres is running (see Known issues).
- Test, single: `sh mvnw -B test -Dtest=MinidropboxApplicationTests#contextLoads` (~4s).
- Lint/format: none configured (no Checkstyle, Spotless or formatter plugin).
- Startup needs PostgreSQL at `jdbc:postgresql://localhost:5433/minidropbox`. Without it the app exits during startup (`Connection to localhost:5433 refused`).
- All config is in `src/main/resources/application.yml`. There are no profiles and no `.env`. Properties read by the code:
  - `spring.datasource.url|username|password`
  - `jwt.secret` (HS256 key; must be at least 32 bytes) and `jwt.expiration` (ms)
  - `file.upload-dir` and `file.chunk-temp-dir` (optional; falls back to `upload-dir`)
- Override them with `--prop=value` (verified) or with env vars through Spring relaxed binding, e.g. `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `FILE_UPLOAD_DIR` (unverified).

## Project layout
- `pom.xml`: dependencies and build. No profiles, no extra plugins beyond compiler (Lombok processor) and spring-boot.
- `src/main/resources/application.yml`: datasource, JPA (`ddl-auto: update`, `show-sql: true`), JWT and file storage settings.
- `src/main/java/com/minidropbox/minidropbox/MinidropboxApplication.java`: entry point.
- `.../auth/`: `User` entity (table `users`), `UserRepository`, `AuthController` (`/auth/register`, `/auth/login`), `TestController` (`/test/hello`).
- `.../config/`: `SecurityConfig` (stateless, CSRF off), `JwtAuthenticationFilter`, `GlobalExceptionHandler`.
- `.../file/`: `FileController` (all `/api/files/**` routes), `FileService`, `FileShareService`, `ChunkUploadService`. Entities `FileMetadata` (`file_metadata`), `FileShare`, `FileChunkMetadata` (`file_chunks`), plus their repositories.
- `.../file/dto/`: request/response DTOs (`FileResponseDto`, `ChunkUploadInit*`, `ChunkStatusResponse`).
- `.../logs/`: `AuditLog` entity, `ActionType` enum (UPLOAD, DOWNLOAD, DELETE, SHARE, UNSHARE), `AuditLogService`.
- `.../util/`: `JwtUtil` (create and parse tokens), `EndPoints.md` (list of endpoints).
- `src/test/java/.../MinidropboxApplicationTests.java`: the only test (`@SpringBootTest` contextLoads).

## Architecture in short
- Auth: `AuthController` stores a BCrypt-hashed password. `/auth/login` returns the raw JWT as a plain string (subject = email).
- Each request: `config/JwtAuthenticationFilter` reads `Authorization: Bearer <jwt>`, loads the `User` by email and sets `ROLE_<role>`. A bad token returns 401 directly from the filter.
- `file/FileController` turns `Authentication.getName()` (the email) into a `User`, then calls a service.
- Simple upload: `FileService.uploadFile` writes `{upload-dir}/{userId}/{uuid}_{name}`, saves `FileMetadata`, then `AuditLogService.log`.
- Download is allowed for the owner, or for anyone with a `FileShare` row, and returns a `UrlResource`. Delete is a soft delete (`deleted` + `deletedAt`). Lookups use the `*AndDeletedFalse` repository methods.
- Chunked upload (`file/ChunkUploadService`):
  - `POST /chunks/init` creates a `FileChunkMetadata` row (status `IN_PROGRESS`) and a temp dir.
  - `POST /chunks/upload` writes `chunk_N` and increments `uploadedChunks`.
  - `POST /chunks/complete` joins the chunks in order into the user dir, saves `FileMetadata` plus an audit row, marks the session `COMPLETED` and deletes the temp files.
  - `DELETE /chunks/{id}/cancel` marks it `CANCELLED` and cleans up.
- Sharing (`file/FileShareService`): only the owner can share or revoke. Each action writes an audit row.

## Conventions
- Packages are grouped by feature (`auth`, `file`, `logs`), with shared code in `config` and `util`. DTOs live in `file/dto`.
- Lombok throughout. `file/` and `logs/` use `@RequiredArgsConstructor` and `@Builder` with `final` fields. `auth/` and `config/` use explicit constructors.
- Logging: `@Slf4j` with `{}` placeholders, `log.info` on each service action.
- Errors: services mostly `throw new RuntimeException("...")`. `GlobalExceptionHandler` maps only `java.nio.file.AccessDeniedException` -> 403, `FileNotFoundException` -> 404 and `IllegalArgumentException` -> 400.
- Chunk session status is a plain `String` (`IN_PROGRESS|COMPLETED|FAILED|CANCELLED`), not an enum.
- File `size` is stored and returned in KB as a `double` (`bytes / 1000.0`).
- Indentation varies by file: `FileController` uses 8 spaces, most files use 4, the main class and test use tabs. Match the file you are editing.
- Tests: JUnit 5 with `@SpringBootTest`. Test starters for data-jpa, security and webmvc are on the classpath but unused.
- Commits: short plain-English summaries with no prefix, usually past tense, e.g. "Chunk Based Uploads Implemented", "File share, share revoke and list shared files implemented".

## Do NOT
- Rename or remove entity fields or tables (`users`, `file_metadata`, `file_chunks`, and the default-named tables for `FileShare` and `AuditLog`) without asking. The schema comes from Hibernate `ddl-auto: update`, and there is no Flyway or Liquibase, so renames leave old columns behind.
- Change REST paths, parameter names or response shapes under `/auth/**` and `/api/files/**`. `util/EndPoints.md` and `CHUNK_UPLOAD_IMPLEMENTATION.md` document them, and login returns a bare token string.
- Add credentials or keys to `application.yml`, and do not copy the existing datasource password or `jwt.secret` values anywhere. Use overrides instead.
- Add dependencies or bump Spring Boot or springdoc without asking (springdoc is already incompatible, see Known issues).
- Reformat whole files or "fix" the mixed indentation in unrelated code.
- Bypass the soft-delete filter. Use the `findBy...AndDeletedFalse` methods for user-facing reads.
- Exercise uploads with the default `file.upload-dir` from the repo root. It creates an untracked `C:/...` directory tree (see Gotchas).

## Gotchas
- JDK: the pom targets release 17. The build was verified on JDK 17 and on JDK 26. On 26, Lombok prints `sun.misc.Unsafe::objectFieldOffset` deprecation warnings, which are harmless for now.
- `file.upload-dir` and `file.chunk-temp-dir` are Windows paths (`C://Users//Lenovo//...`). On macOS/Linux they resolve to a relative `C:/Users/Lenovo/...` path under the working directory, and that path is not gitignored.
- `ChunkUploadService` appends `chunks/{uploadId}` to `chunk-temp-dir`, so temp files end up in `.../chunks/chunks/{uploadId}`.
- `servlet.multipart.*` sits at the top level of `application.yml`, not under `spring:`, so Spring ignores it. Boot's default multipart limits apply instead (unverified at runtime), which may reject 5MB chunks.
- `file.chunk-size`, `file.max-chunk-size` and `file.max-concurrent-chunks` are not read anywhere in the code.
- `SecurityConfig` permits `/files/**`, but the real routes are `/api/files/**`, so every file route needs a JWT. A request with no token gets 403, not 401 (observed).
- There is no `@Transactional` or `@Version` anywhere.
  - `uploadedChunks` is a read-modify-write counter.
  - `FileService.deleteFile` calls `save()` before it sets the deleted flag (unverified whether the flag persists).
  - Re-uploading the same chunk fails because `Files.copy` runs without `REPLACE_EXISTING`.
- `CHUNK_UPLOAD_IMPLEMENTATION.md` claims things the code does not do: an error-status table (e.g. 404 for session not found), "optimistic locking" and config defaults. Trust the code.
- `/test/hello` is `permitAll`, but it calls `authentication.getName()`, so a request without a token hits a null `Authentication` (read from the code, not run).

## Known issues
- `sh mvnw -B test` fails without a local Postgres: 1 test, 0 failures, 1 error. `contextLoads` -> `PSQLException: Connection to localhost:5433 refused` -> `Unable to determine Dialect without JDBC metadata`. There is no H2 or Testcontainers setup.
- springdoc 2.5.0 does not work with Spring Boot 4 / Spring Framework 7. `GET /v3/api-docs` throws `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`, and the client sees a 403. `/swagger-ui.html` only redirects.

## Where things are documented
- `CHUNK_UPLOAD_IMPLEMENTATION.md`: chunked upload design, endpoints, flow, JS client example, future work. Parts of it are out of date (see Gotchas).
- `src/main/java/com/minidropbox/minidropbox/util/EndPoints.md`: list of auth and file endpoints.

## Current status / next steps
- The latest work is chunked uploads (`c635667`, 2026-04-02).
- `origin/localV2` (`67f0463`) is an ancestor of `main`: fully merged, with no unique commits.
- There are no TODO/FIXME comments in the code. Planned ideas are listed under "Future Enhancements" in `CHUNK_UPLOAD_IMPLEMENTATION.md` (checksums, S3, retry and others); none are implemented.
