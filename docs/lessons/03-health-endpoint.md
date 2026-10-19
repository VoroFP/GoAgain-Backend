# Lesson 3: Health endpoint with tests (KAN-23)

What was built in KAN-23, the decisions behind it, and the mistakes worth remembering. It's the first code that follows the structure from KAN-22.

**How to use this lesson:** read part 1 with the code open next to it. Try to answer the self-check questions without looking anything up. If you can't answer one, that's the topic to read about before moving on.

---

## Part 1: What was built and why

### 1.1 The request flow

```
./gradlew run
  → EngineMain reads application.yaml
    → ktor.application.modules (in order):
        es.fervor.goagain.app.SerializationKt.configureSerialization    installs ContentNegotiation + JSON
        es.fervor.goagain.app.health.HealthKt.configureHealth           registers GET /health

GET /health
  → route in app/health/Health.kt
    → call.respond(HealthDTO(status = OK))
      → ContentNegotiation serializes it → 200, Content-Type: application/json, {"status":"OK"}
```

```
src/main/kotlin/es/fervor/goagain/app/
├── Serialization.kt          configureSerialization()
└── health/
    ├── Health.kt             configureHealth(): the GET /health route
    └── HealthDTO.kt          HealthDTO + StatusType enum
src/test/kotlin/es/fervor/goagain/app/health/
└── HealthTest.kt             three in-memory route tests
```

**What to notice**

- **Each module installs one thing.** Serialization and health are separate functions, each listed in `application.yaml`. Adding a module means writing the function *and* adding its line to the YAML.
- **`GET /` is gone.** It returned "Hello, World!" and did nothing useful. A route that does nothing is still code to maintain, and it's another URL that answers anyone scanning the server. `GET /` now returns 404, which is accurate.

### 1.2 Health is infrastructure, not part of the app's API

**Who calls it:** Docker's `HEALTHCHECK`, a load balancer, monitoring tools, and a deploy step that checks a new release came up. This is called a **post-deploy smoke check**. The Android app never calls it.

**Why `/health` and not `/api/v1/health`:**

- `/api/v1` versions the **contract with the Android app**. It changes only when that contract breaks.
- Health is a **contract with infrastructure**. Its path is hardcoded in Docker and load balancer configs, and it should stay the same for the life of the service.
- **Release version and API version are different things.** The release version changes on every deploy (`1.0.0`, `1.0.1` and so on). The API version is the `v1` in the path. Dozens of releases ship under the same `v1`, so `/api/v1/health` and `/health` answer exactly the same question.
- **Health can't tell you *why* a release broke.** If a change breaks startup, *no* endpoint answers, whatever its path. If the process starts but a `v1` endpoint fails, health still returns 200, because it doesn't run that endpoint's code. The *why* comes from the startup logs and from the diff against the last release that worked. Route tests in CI catch broken endpoints before the deploy.

**Why it lives in `app/health` with no layers:** it has no business rules, no data and no Android contract. There's nothing for `domain` or `data` to hold. The README now lists "operational endpoints" as part of `app`'s job.

**Liveness vs. readiness**

- **Liveness:** is the process up? That's all `/health` checks.
- **Readiness:** can it serve real requests? For example, can it reach the database? That arrives with KAN-26.

Keep liveness simple on purpose. If a database outage made liveness fail, Docker would restart the container. Restarting doesn't fix the database, so you get a **restart loop**: the service keeps restarting and never becomes healthy, and every feature goes down for one dependency's problem. For the same reason, there's one health endpoint, not one per feature. Liveness is about the process, and all features share one process.

**What it returns:** only "alive". It needs no auth, because Docker has no token, so anyone can call it. Versions, hostnames and dependency details stay out.

**When the server is down:** the client gets no status code at all, because nothing is there to answer. It gets an **exception**: connection refused or a timeout. A 4xx means the server is up and rejected the request. Behind a load balancer, the *load balancer* answers with 502, 503 or 504 when no instance can reply.

### 1.3 JSON: dependencies vs. installing the plugin

**Three dependencies, three places their versions come from:**

| What | Where it's declared | Version from |
|---|---|---|
| kotlinx.serialization compiler plugin | `libs.versions.toml` `[plugins]`, applied in `build.gradle.kts` | Kotlin (`version.ref = "kotlin"`): it must match the compiler exactly |
| `ktor-server-content-negotiation`, `ktor-serialization-kotlinx-json` | nowhere new: already in the `ktorLibs` catalog | Ktor: all Ktor libraries move together |
| `kotlinx-serialization-json` runtime | not declared at all | comes in transitively, as a dependency of `ktor-serialization-kotlinx-json` |

**Having a dependency is not the same as installing a plugin:**

- The **dependency** puts the code on the classpath, the set of libraries your code can use.
- **`install(ContentNegotiation) { json() }`** inside a module activates it for every request. Without it, `call.respond(someObject)` doesn't know how to produce JSON.

It's the same as Retrofit on Android: adding the dependency does nothing until you call `addConverterFactory(...)`.

**Server vs. client `ContentNegotiation`.** Ktor has two plugins with the same name.

- `io.ktor.server.plugins.contentnegotiation`: your server answering requests.
- `io.ktor.client.plugins.contentnegotiation`: your code calling someone else's API.

The Ktor docs have separate Server and Client sections, and the class names are identical, so check the package when importing. A server never needs an HTTP client to answer requests.

**Minimal configuration on purpose:** plain `json()` with the defaults.

- **No `isLenient`.** It accepts malformed JSON such as unquoted keys. That's useful for a client reading sloppy external APIs, but wrong for a server reading input from clients it doesn't trust.
- **No `prettyPrint`.** It makes every response bigger, and formatting is a KAN-24 convention.

### 1.4 The response: an object and an enum

- **A JSON object, not a bare string.** `{"status":"OK"}` can gain fields later without breaking anyone, which is an additive change. A bare `"OK"` can't, because adding anything turns it into an object, and that's a breaking change.
- **`StatusType` enum instead of `String`.** A typo like `"Ok"` no longer compiles.
- **The enum constant's name *is* the wire value.** Renaming `OK` to `UP` changes the JSON. Anything visible in the response is part of the contract, even an enum constant name.
- **The DTO lives next to the route.** Health has no other layers, so there's no domain model to map to. The "one type per layer" rule protects a domain model, and health doesn't have one.

### 1.5 Tests

All three tests use `testApplication { configure() }`, so they test the whole server **in memory**. `configure()` loads the real `application.yaml`. A wrong module path, such as a leftover line after deleting `Routing.kt`, fails the tests, even though the compiler can't check those strings.

| Test | What it protects |
|---|---|
| `health returns 200` | the endpoint exists and succeeds |
| `health body is status OK` | the exact JSON on the wire: field name and value |
| `health responds with JSON content type` | the server *declares* JSON, which clients use to decide how to parse |

**What went wrong first, and why it matters:** the first body test deserialized the response into `HealthDTO` and failed with `NoTransformationFoundException`. The test's HTTP client had no `ContentNegotiation`. That approach was flawed even if it had worked. The test would rebuild the response into the *same class* the server serializes from. Rename the field or the enum constant, and both sides change together: the JSON breaks but the test still passes. **A contract test should check the wire format, not round-trip the production class.**

**Deliberate trade-off: exact string match.** `bodyAsText()` is compared to the full JSON string. That also fails on changes that don't break the contract, such as turning on `prettyPrint` or adding a field. The choice is that tests describe the current behavior exactly, and changing them is part of the cost of a change. Expect to update this test in KAN-24.

**Why test `Content-Type` separately:** if someone replaced `call.respond(...)` with `respondText("{\"status\":\"OK\"}")`, the body test would still pass, but the response would be `text/plain`.

**Conventions set here:**

- **Test folders mirror the main package:** `app/health/HealthTest.kt`.
- **Test names: subject first, then the expected behavior.** For example, `health returns 200`. The report already shows the class name, but repeating the subject makes names unique when you search the whole project.

---

## Part 2: Self-check

Answer these in your own words. Each one maps to a section above.

1. You delete `configureSerialization` from `application.yaml`. What does `GET /health` return, and which test fails? (1.3, 1.5)
2. Why is `/health` outside `/api/v1`? Give the reason in terms of who calls it. (1.2)
3. You release `1.4.0` and the server doesn't start. Does `/health` help you find the cause? What does? (1.2)
4. What does the Android app get when the server process is dead? And what does it get when a load balancer is in front and every instance is dead? (1.2)
5. The database goes down and liveness checks it. What happens to the container, and to the features that don't use the database? (1.2)
6. Why doesn't the kotlinx.serialization plugin get its own version number? (1.3)
7. Why is `isLenient = true` fine in an Android client but wrong on this server? (1.3)
8. A body test deserializes into `HealthDTO`, and someone renames `status` to `state`. Does the test catch it? Why not? (1.5)
9. Name a change that breaks the exact-match body test without breaking the contract. (1.5)

---

## Part 3: What to learn before KAN-24

KAN-24 sets the API contract conventions. This ticket postponed several decisions to it.

- **JSON conventions:** field naming (camelCase vs snake_case), how `null` and default values appear (`explicitNulls`, `encodeDefaults`), `ignoreUnknownKeys` for incoming requests, and whether `prettyPrint` belongs in production.
- **`StatusPages`:** turning domain errors into one JSON error format, and making sure stack traces never reach the client.
- **Versioning:** what `/api/v1` looks like in Ktor routing (`route("/api/v1") { ... }`), and which changes force a `v2`.
- **HTTP status codes in depth:** 400 vs 422 for validation errors, and 404 vs 410.
- **Documenting the contract:** OpenAPI, and how the Android side can rely on it.
- *Question to bring:* when a feature's routes are added, who registers them? Does each feature install its own `routing {}`, like health does now, or does `app` put all routes together in one place?

---

## Official docs

- Ktor content negotiation and serialization (server): https://ktor.io/docs/server-serialization.html
- Ktor testing: https://ktor.io/docs/server-testing.html
- Ktor modules: https://ktor.io/docs/server-modules.html
- kotlinx.serialization guide: https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md
- Docker `HEALTHCHECK`: https://docs.docker.com/reference/dockerfile/#healthcheck
- Kubernetes liveness and readiness probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
