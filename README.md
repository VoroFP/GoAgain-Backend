# GoAgain Backend

The backend for [GoAgain](https://github.com/VoroFP/GoAgain), my Android learning lab. It's where I learn server-side Kotlin with Ktor by building the API the app consumes.

> 🚧 **Work in progress.** The server currently only has a health check. The stack and architecture below are the target, not the current state.

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

## Architecture

> Decided in KAN-22. The code moves into this structure starting with the health endpoint (KAN-23).

A single Gradle module, organized by feature, with Clean Architecture layers inside each feature.

```
es.fervor.goagain
├── app/                composition root: Application modules, plugin installation, wiring, operational endpoints
│   ├── error/          StatusPages configuration and the JSON error response
│   └── health/         liveness endpoint (GET /health)
├── core/               shared kernel: only what several features need and no single feature owns
│   └── domain/
│       └── error/      domain error types (pure Kotlin)
└── <feature>/          one package per feature
    ├── routes/         Ktor routing, request/response DTOs, input validation, DTO ↔ model mappers
    ├── domain/         domain models, use cases (one per action), repository interfaces
    └── data/           repository implementations, data sources, entities, entity ↔ model mappers
```

### Dependency rules

- **Between packages:** `app → <feature> → core`. Nothing depends on `app`, and `core` never imports from a feature.
- **Inside a feature:** `routes → domain ← data`. `domain` is pure Kotlin: no Ktor, database or serialization types. It may only depend on `core.domain`.
- **Types per layer:** DTOs stay in `routes`, entities stay in `data`, and models live in `domain`. Each boundary has a mapper, and the outer layer owns it, because `domain` can't know about DTOs or entities.
- **Errors:** use cases throw the domain errors from `core.domain.error`. Only `StatusPages` in `app` maps them to HTTP status codes and the JSON error body. Features never build error responses themselves.
- **Operational endpoints** such as `/health` live in `app`, outside `/api/v1`. They're for infrastructure (Docker, load balancers, deploy checks), not part of the Android app's API, and they have no domain or data layers. Liveness only reports that the process is up. Dependency checks such as the database belong to a separate readiness check.
- **Features** don't import each other's `routes` or `data`. How one feature reads another's data will be decided when the first case comes up.

### Why a single module

Splitting into Gradle modules now would add build setup and gain nothing for a one-person project with fast builds. The feature packages and dependency rules keep each feature ready to extract later. For now the rules are enforced by convention and code review. The project will split into modules when build times start to hurt, or when the rules keep getting broken.

### Compared to the GoAgain Android app

- **Same ideas:** packages by feature, Clean Architecture layers, a use case for each action, a `core` for shared code and `app` as the composition root.
- **`routes` replaces the UI and ViewModel layer.** It parses a request, calls a use case and responds. It keeps no state between calls.
- **No lifecycle and shared concurrency.** Use cases and repositories are stateless. Many requests from different users run through them at the same time, so any mutable state outside a request is shared.
- **Features are built around resources and business rules, not screens.** Names match the Android features where the domain concept is the same. Some features exist on only one side, such as onboarding (Android only) or auth (backend).
- **Packages, not modules.** Android uses Gradle modules to enforce boundaries. Here the boundaries are packages, until a module split pays off.

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
