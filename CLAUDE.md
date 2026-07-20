# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**What it is:** a single-module Maven project implementing a local **GraphRAG platform** (Retrieval-Augmented Generation + knowledge graph), built as a **modular monolith** on Spring AI 2.0 + Spring Boot 4.1.0 + Java 25, with domain boundaries enforced by Spring Modulith.

**What it's for:** a learning project that simulates a real-world, multitenant, event-driven, resilient, observable architecture — 100% local and free, no paid APIs.

**What it does:** ingests files (PDF, images, CSV, JSON, XML, TXT, MD) through an async fork-join pipeline (extraction/OCR → Markdown → hierarchical chunking → embeddings ‖ knowledge graph), then answers natural-language questions by fusing vector search and graph traversal (RRF), citing sources — via REST and MCP tools, always under strict tenant isolation.

### Documentation hierarchy (read in this order)

| Document | Answers | Role |
|---|---|---|
| `docs/requisitos.md` | **what** | Source of truth: RF01–RF39 + RNF01–04. No scope exists outside them. |
| `docs/rag-plan.md` | **in which order/pieces** | Épicos 0–10, tasks `[N.M]`. Oldest doc in the chain (pre-dates the SDD): use it for ordering only — technical detail in it may be stale; the SDD supersedes it (e.g. `sdd/dados.md` replaces its §7). |
| `docs/sdd.md` + `docs/sdd/` | **how** | Software Design Document: C4 (up to C3), contracts, data models, and the Architecture Decision Log (ADL-001..010). |
| `docs/adr/` | **why** | Point-in-time architecture decisions (ADR-001 storage — amended, ADR-002 Docling, ADR-003 Ollama embeddings). |
| `src/test/resources/features/` | **when it's done** | Executable acceptance criteria: 23 Gherkin (pt-BR) features tagged `@RFxx`. |

Coherence rule: requirements beat everything; BDD and the SDD beat the plan (they were written after it, from the requirements); if code/`compose.yaml` diverge from docs on version/config detail, the repository wins and docs must be updated. Backlog execution state lives in `openspec/` (one change per épico), not in the plan.

### Current implementation stage

**Domain code was reset** (July/2026): the earlier upload implementation was removed to restart guided by requirements + BDD. Implementation is now proceeding épico by épico via OpenSpec changes. **Implemented so far: Épico 0 (foundations) and Épico 1 (RF01–RF07, ingestion & validation).** Baseline that exists since the reset:

- Module skeleton: `Application` + `api`/`chat`/`rag`/`mcp` (only `package-info.java` with `@ApplicationModule`) + `shared` with the project conventions (`ApplicationException`/`HttpApplicationException`, `Logger`/`LoggerFactory`/`Slf4JLogger`). `mvn compile` passes.
- Full BDD suite: 23 features / ~150 scenarios (runner `CucumberTest` filters `not @pendente`); scenarios leave `@pendente` as their RF is implemented. Steps not yet automated throw `PendingException`.
- Complete local infra in `compose.yaml` (see table below) and OTel instrumentation wired to the LGTM stack.
- SDD complete (`docs/sdd/`), written from an architecture discovery session (July/2026).

**Épico 0 debts: all resolved (July/2026, change `openspec/changes/epico-0-fundacoes`).** What now exists on top of the skeleton: `mvnw` wrapper; Flyway `V1__baseline_documents.sql` per `sdd/dados.md` §2; Keycloak 26.7.0 in `compose.yaml` (realm `graphrag` versioned at `infra/keycloak/data/import/`, dedicated DB created by the Postgres init script `infra/postgres/`); the app is an OAuth2 resource server (`CallerContext` in `shared`, security in `api/internal/security` — 401 without token/`tenantId` claim, zero clock skew); MinIO/JuiceFS credentials unified in `infra/minio/.env.minio`; `TestcontainersConfiguration` on the 2.x API with Postgres/Neo4j/Keycloak; E2E/BDD harness live (`CucumberSpringConfiguration` + `KeycloakTokens`) — the two `@RF35` token scenarios run green, the rest of the suite remains `@pendente`.

**Épico 1: done (July/2026, change `openspec/changes/epico-1-ingestao-e-validacao`).** RF01–RF07 live: `POST /api/v1/documents` (202 with `{id, status, correlationId, version}`, role `document:upload`) running the 8-step validation chain from `sdd/ingestao.md` §2 (size → filename → empty → real MIME via Tika → structural integrity `CORRUPTED_FILE` → duplicate → quota → malware), RAW artifact stored behind the `DocumentStorage` port (POSIX adapter, `app.storage.base-dir`), metadata/history persisted by `rag` behind `DocumentCommandApi` (the declared `api → rag` dependency), `V2__tenant_quotas.sql` (quota is opt-in: no row = no limit). **ClamAV was deferred** ([1.3]): the `MalwareScanner` port ships with an EICAR-aware mock — swapping in the real `clamd` adapter is the only remaining work. Re-upload after `FAILED` registers as `version+1`. Both `ingestao/` features run green except the explicit-reprocess scenario (`@pendente` until a pipeline/`/reprocess` exists, Épico 2+).

**Post-Épico-1 organization cleanup (July/2026, same change):** the upload/validation code went through a package reorganization pass — every module's `internal/` now groups into `configs/controllers/services/repositories/models/dtos/validations/security/mappers/errors` (whatever doesn't fit stays unpackaged); all `@Component`/`@Service`/`@Repository`-only classes were replaced by explicit `@Bean` factory methods in `configs/*Config.java` (the `rag`/`shared` modules already followed this — `api` was the holdout); the error hierarchy's suffix changed from `Error` to `Exception` (see below); reusable code with no module-specific dependency moved to `shared` (`Bytes` binary-search helper, now `shared.support`, a named interface). No RF/BDD contract changed — only internal organization.

> OpenSpec is the backlog-execution layer (one change per épico, referencing the SDD): active changes live in `openspec/changes/`, finished ones are archived under `openspec/changes/archive/` and their delta specs are synced into `openspec/specs/` (the catalog of what is actually implemented).

**Proposed (not yet implemented): `openspec/changes/auth-phantom-token`.** Hardens the RF35/[9.4] user-login flow with the Phantom Token pattern (Redis-backed): the API issues an opaque token to the end user instead of the raw Keycloak JWT, and resolves it internally per request — service-account/MCP JWTs are unaffected. Lives inside `api` as a new `api/internal/auth/` package (not a new Spring Modulith module — `mcp` never does password-grant login, so there's nothing else that would consume it), a second documented layout exception alongside `shared`. Remaining open decisions (endpoint paths; whether logout ships in the same change) are logged in the change's `design.md`.

## Build & Run Commands

```bash
# Build
./mvnw clean package

# Run tests (includes modularity verification + active BDD scenarios)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ModularityTest

# Run in dev mode (Testcontainers instead of Docker Compose)
./mvnw spring-boot:test-run

# Build Docker image
./mvnw spring-boot:build-image
```

## Infrastructure

Provisioned via `compose.yaml` (source of truth; Spring Boot Docker Compose support) or, for tests, `TestcontainersConfiguration`.

| Service | What it's for | Status |
|---|---|---|
| PostgreSQL 18.x (+ Adminer) | Metadata, lifecycle/history, errors, audit, quotas (Flyway); also hosts the dedicated `keycloak` database (init script `infra/postgres/`) | ✅ in `compose.yaml` |
| Neo4j 5.26 Community | Knowledge graph: `Document`/`Chunk` (with `openSearchId`)/`Entity` (with aliases + name embeddings) | ✅ in `compose.yaml` |
| OpenSearch 3.x (+ Dashboards) | Child-chunk index: k-NN + BM25 + tenant filter metadata | ✅ in `compose.yaml` |
| MinIO + JuiceFS + Redis | Object storage mounted as POSIX (`DocumentStorage`, ADR-001; stages `RAW`/`EXTRACTED`/`TRANSFORMED`) | ✅ in `compose.yaml` (creds in `infra/minio/.env.minio`) |
| Ollama (`qwen3:8b` + `nomic-embed-text`) | Chat/extraction LLM + embeddings, both resident (`OLLAMA_MAX_LOADED_MODELS=2`, ADR-003) | ✅ in `compose.yaml` |
| Docling Serve (CPU) | PDF/image parsing, OCR, tables (ADR-002) | ✅ in `compose.yaml` |
| Grafana OTel-LGTM | Traces/logs/metrics (deep observability postponed — Épico 10) | ✅ in `compose.yaml` (port 3000) |
| Keycloak 26.7.0 | AuthN: JWT, realm `graphrag` versioned as JSON, `tenantId` claim (ADL-008); admin console at :8080 (admin/admin, dev) | ✅ in `compose.yaml` (`infra/keycloak/`) |
| ClamAV | Malware scan before `UPLOADED` (RF02) | ⏳ deferred (change epico-1): `MalwareScanner` port + EICAR-aware mock in place; real `clamd` adapter pending ([1.3]) |
| NATS | External messaging / fair queueing (RF12/RF39) | ⏳ Épico 3 ([3.4]) |
| GLiNER sidecar | Zero-shot NER, labels = the RF21 ontology (ADL-006) | ⏳ Épico 6 ([6.1]) |

The app listens on port `8090` by default (`APP_PORT`, `APP_HOST`); compose services use each product's default port (Grafana 3000, Keycloak 8080, Adminer remapped to 8081).

## Architecture

### Modular Structure (Spring Modulith)

```
com.github.overz/
├── Application.java      ← @SpringBootApplication + @Modulithic
├── api/                  ← HTTP entry: upload + validations, status/history, query REST, admin
├── rag/                  ← the whole pipeline: extraction, chunking, embeddings, graph,
│                            hybrid retrieval + RRF, generation, GC, reconciliation
├── chat/                 ← placeholder — OUT OF SCOPE (ADL-007); extension points in docs/sdd/consulta.md
├── mcp/                  ← MCP server: query/graph tools for external agents
└── shared/               ← cross-module contracts: errors, logging, domain events,
                             DocumentStorage port, CallerContext
```

### Module Dependency Rules (per SDD)

- `shared` → available to all modules (`ApplicationModule.Type.SHARED`).
- `api → rag` and `mcp → rag` are the **only** direct cross-module dependencies, declared via `allowedDependencies` and restricted to `rag`'s public interfaces (`RagQueryApi`, `DocumentCommandApi`) — synchronous query/commands only.
- The ingestion **pipeline is 100% event-driven**: `api` publishes, `rag` consumes (Modulith event publication registry, at-least-once). `rag` knows nobody.
- Any `internal/` sub-package is private to its module.
- `ModularityTest` (`ApplicationModules.verify()`) fails the build on violations and generates PlantUML docs into `target/modulith-docs/`.

## Code Conventions

House rules for **all** code in this repository.

**Immutability**

- Data carriers (DTOs, value objects, domain events, request/response bodies) → `record`, always, `implements Serializable`.
- A record needing builder-style construction → Lombok `@Builder`; a record that Jackson actually *deserializes* (via the builder, from an inbound JSON request body) → also `@Jacksonized`. Outbound-only response records don't need it — Jackson serializes records natively without it, and `@Jacksonized` only wires the builder for reads.
- A record representing a true value object validates its own invariants in the compact constructor (non-null/non-blank/shape) — not the business-rule rejections a validation chain already owns with its own stable HTTP `code` (don't duplicate those in the constructor).
- Services/components → `private final` fields + Lombok `@RequiredArgsConstructor`. No field injection, no setters, no mutable state after construction.
- Local variables → `var` (or `final var`) everywhere.
- Well-known, unmaintained-CVE utility libraries (e.g. `commons-lang3`) are welcome in place of hand-rolled helpers — don't reinvent `StringUtils.isBlank` etc.

**Dependency Injection — `@Configuration` + `@Bean`, no stereotypes**

- `@Component`/`@Service`/`@Repository` are **not** used on hand-written classes in this codebase — every bean is registered explicitly via a `@Bean` factory method in a `X/internal/configs/*Config.java` class (one per module: `RagConfig`, `SharedConfig`, plus one or more in `api`). Ordering/composition (e.g. the upload validation chain) is built explicitly as a `List.of(...)` inside the `@Bean` method — no `@Order` magic.
- `@RestController` keeps its annotation (Spring MVC needs it on the class to register routes) but the controller instance is still created by an explicit `@Bean` method, not by component-scan.
- Spring Data repository interfaces are the one exception — the JPA/Neo4j autoconfiguration discovers and implements them on its own; no `@Bean` needed for those.
- Business parameters (limits, allowlists, thresholds) belong in `application.yaml` under `app.*`, bound via a `@ConfigurationProperties` record (`Application` already has `@ConfigurationPropertiesScan`). Format invariants (magic bytes, fixed protocol constants) stay hardcoded — only real business knobs go in config.

**Package layout (per module)**

- Inside each module's `internal/` (or the module root for its public API): `configs/`, `controllers/`, `services/`, `repositories/`, `models/`, `dtos/`, `validations/`, `security/`, `mappers/`, `errors/`. Whatever doesn't fit stays unpackaged.
- `shared` is the exception: it organizes by `@NamedInterface` (`errors`, `logging`, `security`, `storage`, `support`, ...) instead, since that's the surface Spring Modulith actually enforces for `shared` — don't force the `api`/`rag`-style folder set onto it.
- Code used by more than one module belongs in `shared` behind a named interface, never duplicated or reached into across module boundaries (`internal.*` packages are private to their own module — the `ModularityTest` enforces this).

**Error Hierarchy (by Domain)**

- `ApplicationException` (`shared`, abstract, extends `RuntimeException`) — root of every deliberate exception.
- `HttpApplicationException` (`shared`, abstract) — errors crossing an HTTP boundary; carries `HttpStatus` and renders itself as `ProblemDetail` (RFC 9457).
- Every concrete error class ends in `Exception` and lives in its module's `errors/` package, named by domain (e.g. `FileTooLargeException`, `DuplicateFileException` in `api`; a future parsing failure in `rag` would be e.g. `ReaderException`) — grouped by package/module placement and naming, not by a subclass intermediary forced between modules. Each carries the stable `code` used by BDD assertions.
- One `@RestControllerAdvice` (named `*ExceptionHandler`) per module with HTTP — never per endpoint.
- Pipeline errors are **not** HTTP exceptions: they become `*_FAILED` statuses + `processing_errors` rows (see `docs/sdd/resiliencia-e-operacao.md`).

**Centralized Logging**

- `Logger` (`com.github.overz.shared`) — the interface every class logs through; obtained from the project's `LoggerFactory` (`shared`), backed today by SLF4J (`Slf4JLogger`) and cached per class (`ClassValue`). Never call SLF4J's `LoggerFactory.getLogger(...)` directly in application code.
- Log lines must say what happened and why; pipeline logs always include `documentId` and `correlationId`.

## Key Technology Decisions (summary — detail in the SDD/ADL)

- **Lifecycle (RF08):** `RECEIVED → VALIDATING → UPLOADED → QUEUED? → EXTRACTING → TRANSFORMING → CHUNKING → [EMBEDDING ‖ GRAPH_BUILDING] → COMPLETED | PARTIALLY_COMPLETED | FAILED`, with `embeddingStatus`/`graphStatus` sub-states. Only `DocumentLifecycleService` transitions status.
- **Identity:** JWT + Keycloak from day 1 (single realm, `tenantId` claim, `ownerId` = `sub`); controllers only see `CallerContext`. No mocked-auth phase.
- **Multitenancy:** `tenantId`/`ownerId`/`isActive` structural in every store and **every** read filter; cross-tenant resources answer `404`, not `403`.
- **Retrieval:** vector top-N ‖ query-NER + 1–2 hop graph traversal, fused with RRF (k=60); parent chunks compose the prompt; stateless synchronous JSON response with citations (`degraded: true` when generation is skipped).
- **Embeddings:** `nomic-embed-text` via Ollama with mandatory `search_document:`/`search_query:` prefixes; pt-BR validation via golden set before volume; model swap = full reindex (alias `chunks-v1` → `chunks-v2`).
- **Testing:** deterministic AI stubs in the default build; real models only in the golden-set evaluator. Tests assert invariants; the golden set measures quality.

## BDD Workflow (how an RF "closes")

1. Implement the RF in its module.
2. Replace the `PendingException` in the matching steps (`com.github.overz.bdd.steps`) with real automation (add `@CucumberContextConfiguration` + `@SpringBootTest` when Spring context is needed — `cucumber-spring` is on the classpath).
3. Remove the `@pendente` tag from the scenario/feature.
4. `./mvnw test` — the scenario must pass; `ModularityTest` keeps guarding boundaries.

Definition of Done for every backlog task: its `@RFxx` scenarios pass without `@pendente`.

## HTTP Request Files

Every endpoint — one this project **serves** (a controller) or one it **consumes** (e.g. the
Keycloak token endpoint) — gets a `.http` request file under `docs/http/<domain>/`, one file
per domain folder (`ingestao/`, `keycloak/`, ...), numbered in call sequence (`01-`, `02-`, ...).
A file documents every request needed to actually exercise the endpoint (auth included) —
not just a bare example. When a new controller or an external dependency lands, add its
`.http` file in the same change.

## Dev Mode Entrypoint

`TestApplication` (in `src/test/`) runs the real `Application` with Testcontainers instead of Docker Compose:

```bash
./mvnw spring-boot:test-run
```

Runs the real app with Postgres, Neo4j and Keycloak (same versioned realm as compose) as Testcontainers.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
