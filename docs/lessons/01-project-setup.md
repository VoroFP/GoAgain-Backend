# Lesson 1: Project setup (KAN-21)

What the project contains after KAN-21, why each piece is there, and what to learn before starting KAN-22.

**How to use this lesson:** read part 1 with the code open next to it. Try to answer the self-check questions without looking anything up. If you can't answer one, that's the topic to read about before moving on.

---

## Part 1: What the project has

### 1.1 The Gradle build

```
settings.gradle.kts           which projects exist, where dependencies come from, version catalogs
build.gradle.kts              how this project is built: plugins, dependencies, JVM version, main class
gradle/libs.versions.toml     your version catalog (Kotlin, Logback)
gradle.properties             Gradle and Kotlin flags (official code style)
gradlew, gradlew.bat          Gradle wrapper scripts: run the exact Gradle version the project needs
gradle/wrapper/               the wrapper jar and the pinned Gradle version (9.5.1)
```

**What to notice**

- **Two version catalogs.** `libs` is your own (`gradle/libs.versions.toml`). `ktorLibs` is downloaded from Maven: `io.ktor:ktor-version-catalog:3.6.0`, declared in `settings.gradle.kts`. To upgrade Ktor, you change one number in `settings.gradle.kts`, and every Ktor library moves together.
- **Toolchains.** `jvmToolchain(21)` tells Gradle to compile with JDK 21, whatever JDK is running Gradle itself. The `foojay-resolver-convention` plugin downloads that JDK if it's missing. That's why the README only asks for JDK 17+ to *run* Gradle.
- **The `application` plugin** (applied by the Ktor plugin) gives you `./gradlew run`, and `mainClass` tells it what to start.
- **The wrapper is committed on purpose.** CI and anyone who clones the repo get the same Gradle version without installing it.

**Coming from Android:** it's the same Gradle you already know, minus the Android Gradle Plugin. There are no build types, flavors or variants. A server project builds one JVM artifact.

### 1.2 How the server starts

```
./gradlew run
  → mainClass = io.ktor.server.netty.EngineMain      (build.gradle.kts)
    → reads src/main/resources/application.yaml
      → ktor.deployment.port: 8080                   starts Netty on port 8080
      → ktor.application.modules:                    calls each module in order
          es.fervor.goagain.RoutingKt.configureRouting
            → routing { get("/") { ... } }           GET / responds "Hello, World!"
```

**What to notice**

- **Ktor has two ways to start a server.**
  - `EngineMain`, which you're using: the server is described in a config file (`application.yaml`). The port and the list of modules live outside the code.
  - `embeddedServer(Netty, port = 8080) { ... }`: everything is configured in code, inside your own `main()`.

  You chose `EngineMain`, which is why the generated `main.kt` was dead code.
- **A module is just an extension function on `Application`.** `configureRouting` is a module. Later tickets add more of them: serialization, error handling, DI, the database. Each module installs plugins or defines routes.
- **`RoutingKt` is a class you never wrote.** Kotlin compiles top-level functions in `Routing.kt` into a JVM class called `RoutingKt`. The YAML refers to that compiled name as a string, so the compiler can't check it. You saw the consequence during the package rename.
- **`call`** is the current request and response pair. Each request is handled in a coroutine, so route handlers can call `suspend` functions.

**Coming from Android:** `Application` here plays a similar role to Android's `Application` class plus the DI graph: the place where everything gets wired up once at startup. One big difference: a server handles many requests **concurrently**, and it handles them for different users. Any mutable state that lives outside a request is shared between all of them.

### 1.3 The engine: Netty

The engine is the actual HTTP server underneath Ktor: it accepts TCP connections and parses HTTP. Your routes don't know which engine runs them. Switching from Netty to CIO or Jetty means changing one dependency, plus `mainClass`.

### 1.4 Logging

`src/main/resources/logback.xml` configures Logback, the logging implementation. Ktor logs through SLF4J, a logging API, and Logback writes those logs to the console in the pattern defined there.

**Coming from Android:** this is the server equivalent of Logcat. Unlike Logcat, server logs are often your *only* view of a problem in production, so their format and level matter.

### 1.5 Tests

`src/test/kotlin/es/fervor/goagain/ServerTest.kt` uses `testApplication`. It runs the whole server **in memory**: no real port and no network. `configure()` loads the real `application.yaml`, so the test fails if the module path is wrong. `client.get("/")` sends a request through the full Ktor pipeline.

**Coming from Android:** it's closer to a Compose UI test on a JVM than to a unit test. It exercises routing, plugins and serialization together, but runs fast because there's no device.

### 1.6 Repository hygiene

- **`.gitignore`** follows one rule: anything local to your machine, generated, or secret stays out. The single exception is `gradle-wrapper.jar`.
- **Package `es.fervor.goagain`** matches the Android app. Folders mirror the package path, in both `main` and `test`.
- **`LICENSE`** is Apache 2.0, the same as the app.
- **`README.md`** describes the project for visitors: what it is, its stack, the planned architecture and how to run it.
- **`CLAUDE.md`** holds the rules for working with Claude Code in this repo (mentor mode).
- **`.claude/skills/ticket/`** is the `/ticket KAN-XX` skill, which starts a Jira ticket.
- **The IntelliJ MCP server** is registered at *local* scope (in `~/.claude.json`), not in the repo, because it only exists on your machine.

---

## Part 2: Self-check

Answer these in your own words. Each one maps to a section above.

1. What breaks, and when, if `gradle-wrapper.jar` is ignored by git? (1.1)
2. You run `./gradlew run` with JDK 17 installed, and the project compiles with JDK 21. How? (1.1)
3. If you add a new module function `configureSerialization`, what else must change for it to run, and what happens if you forget? (1.2)
4. Why does the YAML say `RoutingKt` when the file is called `Routing.kt`? (1.2)
5. Two requests arrive at the same time. Do they run on the same thread? Can they see each other's local variables? What about a top-level `var` in `Routing.kt`? (1.2)
6. What would you change to move from Netty to CIO? (1.3)
7. Why does `ServerTest` catch a typo in `application.yaml`, while `./gradlew compileKotlin` doesn't? (1.5)

**Bonus: find the bug.** Look at the date pattern in `logback.xml`. Compare `YYYY` with `yyyy` in the Logback (or Java `SimpleDateFormat`) docs. What would your logs show on December 29? Then find the logger that's configured for an engine you don't use.

> **Note:** both bugs came from the Ktor generator, and were fixed before the first commit (`d1d54fb`). The pattern now uses `yyyy`, and the `org.eclipse.jetty` logger is gone. A project generated at https://start.ktor.io may still include them, unless the template has changed since.

---

## Part 3: What to learn before the next tickets

You don't need all of this before KAN-22. Learn each block right before its ticket.

### Before KAN-22: project structure and layering

- **Kotlin visibility:** `internal` vs `private` vs `public`, and what a "module" means for `internal` (a Gradle module, not a Ktor module).
- **Package-by-layer vs package-by-feature:** what each one optimizes for, and what you chose in the Android app with `:feature:<name>` modules.
- **Ktor's route organization:** `Route` extension functions and `route("/path") { ... }` nesting. This is how routes are split across files without one giant `routing {}` block.
- *Question to bring:* should `routes`/`domain`/`data` be top-level packages, or live inside each feature?

### Before KAN-23: health endpoint with tests

- **Health checks:** what liveness vs readiness means, and who calls a health endpoint (Docker, a load balancer, monitoring).
- **`testApplication` in depth:** testing a module directly vs loading the config, and asserting on status codes and body content.
- **HTTP status codes:** at least 200, 201, 204, 400, 401, 403, 404, 409, 422 and 500, and when to use each.

### Before KAN-24: API contract conventions

- **HTTP methods and idempotency:** GET, POST, PUT, PATCH and DELETE, and which ones are safe to retry.
- **kotlinx.serialization:** `@Serializable`, the `ContentNegotiation` plugin, and how nullability and default values affect the JSON.
- **Ktor's `StatusPages` plugin:** turning exceptions into HTTP responses in one place.
- **API versioning and backwards compatibility:** which changes break an already released Android app, and which don't.

### Before KAN-25: configuration, environments and DI

- **Ktor configuration:** reading values from `application.yaml`, and overriding them with environment variables.
- **The 12-factor app's "config" chapter:** why config belongs in the environment, not the code.
- **DI options on the server:** Koin, Kodein, or manual constructor injection. What you used Hilt for on Android, and what replaces Hilt's annotation processing here.

### Before KAN-26: persistence

- **SQL basics:** tables, primary and foreign keys, indexes and transactions.
- **Choosing a database library:** for example Exposed, SQLDelight or plain JDBC. Compare SQLDelight with Room, which you know from Android.
- **Migrations:** why the schema is versioned (Flyway, Liquibase), and how that differs from Room's migrations.
- **Blocking vs suspending I/O:** why JDBC calls go to `Dispatchers.IO`.

### Before KAN-27: Docker

- **Images vs containers**, `Dockerfile` basics and multi-stage builds.
- **The Ktor Gradle plugin's fat jar and Docker tasks** vs writing your own `Dockerfile`.
- **docker-compose:** running the server and a database together locally.

### Before KAN-28: CI

- **GitHub Actions concepts:** workflows, jobs, steps, and runners.
- **Caching Gradle in CI** and running the tests on every PR.
- **Branch protection:** requiring the build to pass before merging.

### Before KAN-29: connecting the Android app

- **Why `localhost` on the emulator isn't your Mac** (`10.0.2.2`), and Android's cleartext HTTP restrictions.
- **The client side:** Ktor Client or Retrofit in the app, and how the shared API contract stays in sync on both sides.

---

## Official docs

- Ktor: https://ktor.io/docs/
- Gradle version catalogs: https://docs.gradle.org/current/userguide/version_catalogs.html
- Gradle toolchains: https://docs.gradle.org/current/userguide/toolchains.html
- Kotlin coding conventions (packages and files): https://kotlinlang.org/docs/coding-conventions.html
- kotlinx.serialization: https://github.com/Kotlin/kotlinx.serialization
- The Twelve-Factor App: https://12factor.net/
