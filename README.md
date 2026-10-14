# GoAgain Backend

The backend for [GoAgain](https://github.com/VoroFP/GoAgain), my Android learning lab. It's where I learn server-side Kotlin with Ktor by building the API the app consumes.

> 🚧 **Work in progress.** The server currently responds with a Hello World. The stack and architecture below are the target, not the current state.

## Goals

- **Learn backend from an Android background**: build a real API step by step, understanding each piece before adding the next.
- **Serve the GoAgain app**: a versioned REST API that the Android app can rely on as a contract.
- **Production-minded from the start**: tests, consistent error handling, configuration per environment, Docker and CI.

## Tech stack

- **Kotlin** on the JVM (Java 21)
- **Ktor** server on the **Netty** engine
- **kotlinx.serialization** for JSON *(planned)*
- **REST API** versioned under `/api/v1` *(planned)*
- **Coroutines** for async work
- **Gradle Kotlin DSL** and version catalogs
- **Docker** and **GitHub Actions** for CI *(planned)*

## Architecture (planned)

```
routes   Ktor routing, request/response DTOs, validation, HTTP status mapping
domain   pure Kotlin: use cases, domain models and repository interfaces
data     repository implementations, database access and mappers
```

Dependency rules:

- `domain` depends on nothing else: no Ktor, database or serialization types.
- `routes` and `data` depend on `domain`, never on each other.
- DTOs stay in `routes` and database entities stay in `data`. Mappers translate between them and domain models.

## Getting started

Requirements: JDK 17 or newer to run Gradle. Gradle downloads JDK 21 automatically to build the project.

```
git clone https://github.com/VoroFP/GoAgain-Backend.git
cd GoAgain-Backend
./gradlew run
```

The server listens on http://localhost:8080.

| Task              | Description                        |
|-------------------|------------------------------------|
| `./gradlew run`   | Start the server on port 8080      |
| `./gradlew test`  | Run the tests                      |
| `./gradlew build` | Compile, run the tests and package |

## License

Copyright 2026 Voro Ferrer Peris

Licensed under the [Apache License, Version 2.0](LICENSE).
