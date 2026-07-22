# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**What it is:** a single-module Maven project implementing a local **GraphRAG platform** (Retrieval-Augmented Generation + knowledge graph), built as a **modular monolith** on Spring AI 2.0 + Spring Boot 4.1.0 + Java 25, with domain boundaries enforced by Spring Modulith.

**What it's for:** a learning project that simulates a real-world, multitenant, event-driven, resilient, observable architecture — 100% local and free, no paid APIs.

**What it does:** ingests files (PDF, images, CSV, JSON, XML, TXT, MD) through an async fork-join pipeline (extraction/OCR → Markdown → hierarchical chunking → embeddings ‖ knowledge graph), then answers natural-language questions by fusing vector search and graph traversal (RRF), citing sources — via REST and MCP tools, always under strict tenant isolation.

### Documentation hierarchy (read in this order)

`openspec/` is the single source of truth (since 22/Jul/2026 — `docs/requisitos.md`, `docs/rag-plan.md` and `docs/adr/` were migrated into it and no longer exist; `docs/sdd.md`/`docs/sdd/` are the one piece that stayed put, since they're living architecture reference, not a discrete numbered/versioned artifact).

| Document | Answers | Role |
|---|---|---|
| `openspec/requirements/RF-NN.md` + `RNF-NN.md` | **what** | Source of truth: one file per requirement, RF-01–RF-40 + RNF-01–04. No scope exists outside them. Revisão/complemento sub-variants live inside their parent RF's own file, not as a separate one. |
| `openspec/plans/PLAN-NN.md` | **in which order/pieces** | One file per épico, `PLAN-00`–`PLAN-10` (number = épico number), tasks `[N.M]`. Oldest doc in the chain in spirit (pre-dates the SDD): use it for ordering only — technical detail in it may be stale; the SDD supersedes it (e.g. `sdd/dados.md` replaces what used to be its own §7). `openspec/plans/README.md` holds the cross-épico roadmap/risks/references that don't belong to one specific épico. |
| `docs/sdd.md` + `docs/sdd/` | **how** | Software Design Document: C4 (up to C3), contracts, data models, and the Architecture Decision Log (ADL-001..010). |
| `openspec/decisions/ADR-NNN.md` | **why** | Point-in-time architecture decisions (ADR-001 storage — amended, ADR-002 Docling, ADR-003 Ollama embeddings, ADR-004 phantom token). Decisions scoped to a single change stay in that change's own `design.md`, they don't get promoted to a global ADR. |
| `openspec/technical-debt/TD-NNN.md` | **what's still owed** | Open technical debt: a mock standing in for a real integration, an accepted risk, a cut corner — origin, current mitigation, planned resolution. |
| `src/test/resources/features/` | **when it's done** | Executable acceptance criteria: 23 Gherkin (pt-BR) features tagged `@RFxx`. |

Coherence rule: requirements beat everything; BDD and the SDD beat the plan (they were written after it, from the requirements); if code/`compose.yaml` diverge from docs on version/config detail, the repository wins and docs must be updated. Backlog execution state lives in `openspec/changes/` (one change per épico, or per ad-hoc initiative outside épico numbering) — same system as the requirements/plans/decisions/debt above, not a separate layer.

### Traceability workflow (manual — the CLI doesn't manage this)

`requirements/`, `plans/`, `decisions/`, `technical-debt/` are plain markdown the OpenSpec CLI has zero awareness of — its `spec-driven` schema only knows 4 artifact types (`proposal`, `specs`, `design`, `tasks`), so `openspec validate`/`list`/`archive`/`doctor` never touch these folders. Linking discipline instead of tooling:

- Every `openspec/specs/*/spec.md` Purpose cites the `RF-NN.md`(s) it covers — link lives inside the CLI-managed spec, not as a separate pointer file.
- Every new/touched change's `proposal.md`/`design.md`/`tasks.md` cites the `PLAN-NN.md`, `ADR-NNN.md`, `TD-NNN.md` it touches.
- Direction is always inward-to-outward: the CLI-managed artifact (spec, change) links out to the unmanaged one (RF/PLAN/ADR/TD) — never the reverse.
- New RF/RNF → new/edited `RF-NN.md` directly (same as editing the old `requisitos.md`). New durable cross-cutting decision → new `ADR-NNN.md`. Debt accepted during a change → new `TD-NNN.md`, referenced from that change's `design.md`.
- No automated consistency check exists. Verification is a manual grep-and-read sweep (broken links, stale paths) done on request or after a change that touches several of these files — not run on every commit.

### Current implementation stage

**Domain code was reset** (July/2026): the earlier upload implementation was removed to restart guided by requirements + BDD. Implementation is now proceeding épico by épico via OpenSpec changes. **Implemented so far: Épico 0 (foundations), Épico 1 (RF01–RF07, ingestion & validation), and Épico 2 (RF08–RF11, document lifecycle).** Baseline that exists since the reset:

- Module skeleton: `Application` + `api`/`chat`/`rag`/`mcp` (only `package-info.java` with `@ApplicationModule`) + `shared` with the project conventions (`ApplicationException`/`HttpApplicationException`, `Logger`/`LoggerFactory`/`Slf4JLogger`). `mvn compile` passes.
- Full BDD suite: 23 features / ~150 scenarios (runner `CucumberTest` filters `not @pendente`); scenarios leave `@pendente` as their RF is implemented. Steps not yet automated throw `PendingException`.
- Complete local infra in `compose.yaml` (see table below) and OTel instrumentation wired to the LGTM stack.
- SDD complete (`docs/sdd/`), written from an architecture discovery session (July/2026).

**Épico 0 debts: all resolved (July/2026, change `openspec/changes/epico-0-fundacoes`).** What now exists on top of the skeleton: `mvnw` wrapper; Flyway `V1__baseline_documents.sql` per `sdd/dados.md` §2; Keycloak 26.7.0 in `compose.yaml` (realm `graphrag` versioned at `infra/keycloak/data/import/`, dedicated DB created by the Postgres init script `infra/postgres/`); the app is an OAuth2 resource server (`CallerContext` in `shared`, security in `api/internal/security` — 401 without token/`tenantId` claim, zero clock skew); MinIO/JuiceFS credentials unified in `infra/minio/.env.minio`; `TestcontainersConfiguration` on the 2.x API with Postgres/Neo4j/Keycloak; E2E/BDD harness live (`CucumberSpringConfiguration` + `KeycloakTokens`) — the two `@RF35` token scenarios run green, the rest of the suite remains `@pendente`.

**Épico 1: done (July/2026, change `openspec/changes/epico-1-ingestao-e-validacao`).** RF01–RF07 live: `POST /api/v1/documents` (202 with `{id, status, correlationId, version}`, role `document:upload`) running the 8-step validation chain from `sdd/ingestao.md` §2 (size → filename → empty → real MIME via Tika → structural integrity `CORRUPTED_FILE` → duplicate → quota → malware), RAW artifact stored behind the `DocumentStorage` port (POSIX adapter, `app.storage.base-dir`), metadata/history persisted by `rag` behind `DocumentCommandApi` (the declared `api → rag` dependency), `V2__tenant_quotas.sql` (quota is opt-in: no row = no limit). **ClamAV was deferred** ([1.3]): the `MalwareScanner` port ships with an EICAR-aware mock — swapping in the real `clamd` adapter is the only remaining work. Re-upload after `FAILED` registers as `version+1`. Both `ingestao/` features run green except the explicit-reprocess scenario (`@pendente` until a pipeline/`/reprocess` exists, Épico 2+).

**Post-Épico-1 organization cleanup (July/2026, same change):** the upload/validation code went through a package reorganization pass — every module's `internal/` now groups into `configs/controllers/services/repositories/models/dtos/validations/security/mappers/errors` (whatever doesn't fit stays unpackaged); all `@Component`/`@Service`/`@Repository`-only classes were replaced by explicit `@Bean` factory methods in `configs/*Config.java` (the `rag`/`shared` modules already followed this — `api` was the holdout); the error hierarchy's suffix changed from `Error` to `Exception` (see below); reusable code with no module-specific dependency moved to `shared` (`Bytes` binary-search helper, now `shared.support`, a named interface). No RF/BDD contract changed — only internal organization.

> OpenSpec is the backlog-execution layer (one change per épico, referencing the SDD): active changes live in `openspec/changes/`, finished ones are archived under `openspec/changes/archive/` and their delta specs are synced into `openspec/specs/` (the catalog of what is actually implemented).

**Done (July/2026, change `openspec/changes/archive/2026-07-20-auth-phantom-token`, `openspec/decisions/ADR-004.md`).** RF35/[9.4] user-login hardened with the Phantom Token pattern (Redis-backed): `POST /api/v1/auth/{login,refresh,logout}` issue/renew/revoke an opaque token for the end user instead of the raw Keycloak JWT, resolved internally per request via `AuthenticationManagerResolver` (`SecurityConfig`) — service-account/MCP JWTs are unaffected, both formats converge on the same `CallerContext`. New `spring.data.redis.*` (db `2` — db `1` is JuiceFS) and `app.auth.keycloak-client-id` config. **Post-implementation reorg (same month):** the flow initially lived in a dedicated `api/internal/auth/` package (a documented layout exception); it was later dissolved into the standard `controllers/dtos/mappers/repositories/security` folders like the rest of `api` — `PhantomTokenIssuer`/`PhantomTokenIssuerImpl` in `security/`, `PhantomTokenRepository`/`PhantomTokenRedisRepository` in `repositories/`, DTOs in `dtos/`, `AuthResponseMapper` in `mappers/`; `OpaqueTokenGenerator` moved to `shared/support/` (no module-specific dependency).

**Épico 2: done (July/2026, change `openspec/changes/archive/epico-2-ciclo-de-vida`).** RF08–RF11 live: `DocumentLifecycleService` (`rag/internal/services/`) is the single write point for `DocumentStatus`, extending `DocumentCommandApi` with `statusOf`/`historyOf`/`deleteDocument`/`replaceVersion`; `DocumentLifecycleController` exposes `GET /api/v1/documents/{id}/status`, `GET /api/v1/documents/{id}/history`, `DELETE /api/v1/documents/{id}` (RF10, owner-only — 403 same-tenant/wrong-owner, 404 otherwise), `POST /api/v1/documents/{id}/versions` (RF10 complement, `version+1`). Soft-delete is synchronous (not event-driven — `SoftDeleteRequestedEvent` in `sdd/dados.md` §5 stays a design-only debt until Épico 3), updating Postgres + Neo4j (`DocumentGraphRepository.markInactive`) + OpenSearch (`ChunkIndex.inactivateByDocumentId`) directly; a store failure logs to `processing_errors` instead of aborting. `EntityGarbageCollectionJob` (`@Scheduled`, `app.gc.interval-ms`) hard-deletes orphan entities (RF11) via `EntityGraphRepository`. Neo4j constraints (`document_id`/`chunk_id`/`entity_id` unique, `chunk_tenant_active` index) and the OpenSearch `chunks-v1` index (alias `chunks`, full mapping from `sdd/dados.md` §3 incl. `knn_vector`) are created by idempotent `ApplicationRunner`s in `RagConfig` — population is still Épico 5/6's job; RF08 (fork-join) and RF10/RF11 (graph/vector) BDD scenarios fixture that state directly in `Dado` steps (`CicloDeVidaSteps`), same pattern as `SyntheticFiles` from Épico 1. **Implementation finding:** `DocumentGraphRepository`/`EntityGraphRepository` are hand-written classes over `Neo4jClient` (`Neo4jDocumentGraphRepository`/`Neo4jEntityGraphRepository`), not `Neo4jRepository` interfaces with `@Query` — Spring Data Neo4j 8.1.0's custom-query derivation proved unreliable here (an `NPE` inside `Neo4jTemplate$DefaultExecutableQuery` for a query with an `EXISTS {}` subquery, a silent no-op for one chaining `WITH`/`OPTIONAL MATCH`) — same raw-client pattern as `OpenSearchChunkIndex`. **Fixed post-implementation, round 1:** soft-delete originally only flipped `is_active`, leaving `document_status_history`/status queries oblivious to the deletion — `softDelete` started writing an audit row and `statusOf`/`historyOf`/`deleteDocument`/`replaceVersion` all filtered `isActive=true`, so a soft-deleted document read as not-found everywhere.

**Fixed post-implementation, round 2 (July/2026):** that blanket rule went too far — it made the audit row itself unreachable via `GET /history` (the whole point of writing it). Visibility split in two: `statusOf` keeps `isActive=true` (a deleted document has no "current status"), `historyOf` drops that filter entirely (the audit trail must survive deletion). `DocumentStatus` gained a `DELETED` terminal value, persisted directly by `softDelete` (not a history-row-only marker) so every reader — `/history`, the RF40 listing below, raw SQL — sees it with no conditional logic of its own; the prior real pipeline status is preserved in that row's `document_status_history.from_status`. Separately, the RF07 duplicate check ignored `isActive`, permanently blocking re-upload of content whose only match was already soft-deleted — fixed by adding `isActive=true` to that query. And `POST /{id}/versions` dropped `DuplicateFileValidator` from its validation chain entirely: reverting to a prior version's content (even one shared with another still-active document) is the caller's call, not the system's to block.

**RF40 — Listagem de Documentos: done (July/2026, change `openspec/changes/archive/2026-07-22-listagem-de-documentos`).** `GET /api/v1/documents` — paginated (`Pageable`/`Page`), `includeInactive=false` by default. Genuine requirement gap found in live use, not an épico item: no RF ever covered listing, only per-id lookup (RF09). Confirmed with the user: visibility is tenant-wide, not owner-scoped — any authenticated user of the tenant sees every document in it, `ownerId` on each item distinguishes who sent what. Consequence: `statusOf`/`historyOf` (RF09) dropped their `ownerId` parameter too and became tenant-wide reads — a shared listing is pointless if the caller can't then open a colleague's document. `DELETE`/`POST /versions` are unchanged, still owner-only. New `documents.updated_at` column (`V3__document_updated_at.sql`) — only `uploaded_at` existed before — auto-touched via `@PrePersist`/`@PreUpdate` on `DocumentEntity`. New `DocumentQueryController`/`DocumentQueryConfig`, `DocumentSummary` (public `rag` record, `id`/`filename`/`status`/`active`/`ownerId`/`version`/`createdAt`/`updatedAt`).

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
- Spring Data JPA repository interfaces are the one exception — the JPA autoconfiguration discovers and implements them on its own; no `@Bean` needed for those. Neo4j repositories are **not** — `Neo4jRepository` with custom `@Query` proved unreliable in this project's Spring Data Neo4j version (silent no-ops and NPEs for anything beyond a trivial single-clause Cypher statement); Neo4j repositories here are hand-written classes over `Neo4jClient`, wired via `@Bean` like everything else (see `Neo4jDocumentGraphRepository`/`Neo4jEntityGraphRepository`, épico-2).
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
