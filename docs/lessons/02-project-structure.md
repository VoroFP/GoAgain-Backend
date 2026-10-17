# Lesson 2: Project structure and layering (KAN-22)

What was decided in KAN-22, why, and how it compares to the GoAgain Android app. The decisions themselves live in the README's **Architecture** section. This lesson explains the reasoning behind them.

**How to use this lesson:** read part 1 with the README open next to it. Try to answer the self-check questions without looking anything up. If you can't answer one, that's the topic to read about before moving on.

---

## Part 1: The decisions and why

### 1.1 Packages by feature, layers inside each feature

```
es.fervor.goagain
├── app/          composition root
├── core/         shared kernel
└── <feature>/
    ├── routes/
    ├── domain/
    └── data/
```

**What to notice**

- **The feature is the top-level unit, not the layer.** Everything about one feature sits in one place, so you can add, change or delete a feature without touching the others. It's also what makes extracting a feature into its own Gradle module realistic later.
- **The layers keep the same rules as before:** `routes → domain ← data`, and `domain` is pure Kotlin. Packaging by feature doesn't replace Clean Architecture. It decides where the layers live.

**Coming from Android:** this is the same idea as your `:feature:<name>` modules, but with packages instead of Gradle modules (see 1.4). On a server, though, features are built around **resources and business rules**, not screens. Some Android features have no backend counterpart (onboarding is pure UI). Some backend features have no screen (auth, a scheduled job). Names match the Android app only where the domain concept is the same.

### 1.2 `app` vs `core`: two opposite roles

| | `app` | `core` |
|---|---|---|
| Who depends on it | nobody | every feature |
| What it depends on | everything | no feature |
| What goes in it | wiring and startup: Ktor modules, plugin installation, putting the routes together | what several features need and no single feature owns |

**What to notice**

- **What separates them is the direction of dependencies, not "needed to run".** `core` code runs in production too. The difference is that features depend on `core`, while `app` depends on the features.
- **Test before adding anything to `core`:** do two or more features need this type, and does it belong to none of them? Without that test, `core` becomes a dumping ground for utils, constants and code that really belongs to one feature.
- **Layering still applies inside `core`.** The domain error types go in `core.domain.error` because use cases throw them, so they must be pure Kotlin.

**Coming from Android:** `app` is your `:app` module, the composition root that knows every implementation. `core` is your `:core:*` modules.

### 1.3 Errors: why they don't live in `app`

`app` depends on every feature, because it installs their routes. If a feature threw an error type defined in `app`, that feature would depend on `app` too. The dependency would go both ways, which is a **cycle**.

In a single module, the compiler doesn't catch it. But the cycle means neither side can be extracted without the other, and every change to one touches the other.

The split you chose:

- **Domain error types** live in `core.domain.error`. Use cases throw them, and they know nothing about HTTP.
- **The mapping to status codes and the JSON error body** lives in `app/error`, configured with `StatusPages`. Only `app` builds error responses. Features never do.

### 1.4 One Gradle module, for now

Splitting into modules now would add build setup and gain nothing for one developer with fast builds. Being *able* to split easily comes from the dependency rules, not from modules existing. Code that only imports in allowed directions can be moved into a module later without changes.

**The trade-off:** without modules, nothing enforces the rules. A wrong import compiles fine. For now, they're enforced by convention and code review.

**The split trigger, written down so it's a decision and not a feeling:** build times start to hurt, or the rules keep getting broken. An architecture test library such as Konsist is a cheaper option in between, because it can check imports in a unit test.

### 1.5 One type per layer, one mapper per boundary

For a single feature:

- **Three types:** a DTO in `routes`, a model in `domain` and an entity in `data`.
- **Two mappers:** DTO ↔ model, owned by `routes`, and entity ↔ model, owned by `data`.

`domain` can't own any mapper, because it can't know about DTOs or entities. When all three types have identical fields, the separate types protect the **API contract**. If you rename a database column, the JSON the released Android app reads doesn't change.

### 1.6 Use cases: one for each action

Each business action is its own use case class. An endpoint is just one way to trigger an action, and it isn't the same thing as a use case. In a mostly CRUD API, many use cases are one-line calls to a repository. You chose to accept that in exchange for consistency.

**Still open:** a use case that writes to two tables needs a transaction. A transaction is a group of database writes that all succeed or all roll back together. Which layer starts and ends it gets decided in KAN-26.

### 1.7 The server-specific rule: stateless shared objects

> **Objects shared between requests must not hold per-request data. Per-request data travels only through the request: parameters, local variables and the `call`.**

Shared objects may hold dependencies that never change, such as a repository reference.

Why: one use case instance serves every request at the same time. Say it had a `var currentUserId`:

1. Alice's request passes auth and sets `currentUserId = alice`.
2. Bob's request passes auth and sets `currentUserId = bob`.
3. Alice's request reads `currentUserId`, gets `bob`, and returns Bob's data to Alice.

Both tokens were valid and auth worked. The leak happens *after* auth, inside the process. This is a **race condition**: the result depends on the timing of two concurrent operations.

**Coming from Android:** one ViewModel serves one user. A race between parallel coroutines there corrupts that same user's state, which is a bug. On the server, the same race mixes different users' data, which is a **security incident**.

---

## Part 2: Self-check

Answer these in your own words. Each one maps to a section above.

1. You want to delete a feature. With packages by layer, how many folders would you touch? With packages by feature? (1.1)
2. Name one Android feature with no backend counterpart, and one backend feature with no Android screen. (1.1)
3. What's the difference between `app` and `core`, in one sentence about dependency direction? (1.2)
4. A date-formatting helper is used by only one feature. Does it go in `core`? (1.2)
5. Why can't the domain error types live in `app`? And why doesn't the compiler stop you from putting them there? (1.3)
6. What does "able to split easily" depend on, if not on having modules already? (1.4)
7. You rename a database column. Which types and mappers change, and what does the Android app notice? (1.5)
8. What's wrong with a use case that stores the user ID in a property? Why doesn't auth prevent the problem? (1.7)

**Open question for later:** if the caller's identity can't live in a property, how does a use case deep in the call chain know who's calling? (Auth work, and KAN-25 for DI.)

---

## Part 3: What to learn before KAN-23

KAN-23 puts this structure to its first real test: the health endpoint is the first code that has to live in a feature package. See also lesson 1, part 3.

- **Health checks:** liveness vs readiness, and who calls a health endpoint (Docker, a load balancer, monitoring).
- **Ktor route organization:** `Route` extension functions, and how a feature's routes get registered from `app` without `app` knowing their internals.
- **Ktor modules and `application.yaml`:** with a module list per package, which module functions does `application.yaml` reference now?
- *Question to bring:* is "health" a feature with `routes`/`domain`/`data`, or is it so small that it only has a `routes` layer? What does your answer say about applying the structure to every feature?

---

## Official docs

- Ktor routing: https://ktor.io/docs/server-routing.html
- Ktor modules: https://ktor.io/docs/server-modules.html
- Ktor `StatusPages`: https://ktor.io/docs/server-status-pages.html
- Konsist (architecture tests for Kotlin): https://docs.konsist.lemonappdev.com/
- Android guide to app modularization (for comparison): https://developer.android.com/topic/modularization
