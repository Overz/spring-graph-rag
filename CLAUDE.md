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

**Domain code was reset.** The earlier upload implementation was removed to restart guided by requirements + BDD. **No RF is implemented yet.** What exists today:

- Module skeleton: `Application` + `api`/`chat`/`rag`/`mcp` (only `package-info.java` with `@ApplicationModule`) + `shared` with the project conventions (`ApplicationError`/`HttpApplicationError`, `Logger`/`LoggerFactory`/`Slf4JLogger`). `mvn compile` passes.
- Full BDD suite: 23 features / ~150 scenarios, all tagged `@pendente` (runner `CucumberTest` filters `not @pendente` — build stays green with zero active scenarios). Skeleton steps in `com.github.overz.bdd.steps` throw `PendingException`.
- Complete local infra in `compose.yaml` (see table below) and OTel instrumentation wired to the LGTM stack.
- SDD complete (`docs/sdd/`), written from an architecture discovery session (July/2026).

**Known debts (all tracked as Épico 0 tasks in `docs/rag-plan.md` §5–6):** missing `mvnw` wrapper scripts ([0.1] — regenerate with `mvn -N wrapper:wrapper`); `TestcontainersConfiguration` Postgres/Neo4j beans commented out pending Testcontainers 2.x API migration ([0.2]); port 3000 conflict between the app default and `grafana-lgtm` ([0.3]); Flyway `V1` follows the pre-requirements schema and must be rewritten ([0.4]); JuiceFS×MinIO credential mismatch in `compose.yaml` ([0.5]); Keycloak not yet provisioned ([0.7]); `chat-memory-repository-neo4j` dependency to be removed ([0.8]).

> OpenSpec is planned as the backlog-execution layer (one change per épico, referencing the SDD) once implementation starts — the `openspec/` directory does not exist yet; don't reference `openspec` commands until it does.

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
| PostgreSQL 18.x (+ Adminer) | Metadata, lifecycle/history, errors, audit, quotas (Flyway); will also host the Keycloak schema | ✅ in `compose.yaml` (schema rewrite pending — [0.4]) |
| Neo4j 5.26 Community | Knowledge graph: `Document`/`Chunk` (with `openSearchId`)/`Entity` (with aliases + name embeddings) | ✅ in `compose.yaml` |
| OpenSearch 3.x (+ Dashboards) | Child-chunk index: k-NN + BM25 + tenant filter metadata | ✅ in `compose.yaml` |
| MinIO + JuiceFS + Redis | Object storage mounted as POSIX (`DocumentStorage`, ADR-001; stages `RAW`/`EXTRACTED`/`TRANSFORMED`) | ✅ in `compose.yaml` (credential mismatch — [0.5]) |
| Ollama (`qwen3:8b` + `nomic-embed-text`) | Chat/extraction LLM + embeddings, both resident (`OLLAMA_MAX_LOADED_MODELS=2`, ADR-003) | ✅ in `compose.yaml` |
| Docling Serve (CPU) | PDF/image parsing, OCR, tables (ADR-002) | ✅ in `compose.yaml` |
| Grafana OTel-LGTM | Traces/logs/metrics (deep observability postponed — Épico 10) | ✅ in `compose.yaml` (port 3000 conflict — [0.3]) |
| Keycloak | AuthN: JWT, realm `graphrag` versioned as JSON, `tenantId` claim (ADL-008) | ⏳ Épico 0 ([0.7]) |
| ClamAV | Malware scan before `UPLOADED` (RF02) | ⏳ Épico 1 ([1.3]) |
| NATS | External messaging / fair queueing (RF12/RF39) | ⏳ Épico 3 ([3.4]) |
| GLiNER sidecar | Zero-shot NER, labels = the RF21 ontology (ADL-006) | ⏳ Épico 6 ([6.1]) |

The app listens on port `3000` by default (`APP_PORT`, `APP_HOST`) — conflicts with `grafana-lgtm` until [0.3] is resolved.

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

- Data carriers (DTOs, value objects, domain events, request/response bodies) → `record`, always.
- A record needing builder-style construction/JSON via builder → Lombok (`@Builder`, `@Jacksonized`) instead of hand-written logic.
- Services/components → `private final` fields + Lombok `@RequiredArgsConstructor`. No field injection, no setters, no mutable state after construction.
- Local variables → `var` (or `final var`) everywhere.

**Error Hierarchy (by Domain)**

- `ApplicationError` (`shared`, abstract, extends `RuntimeException`) — root of every deliberate exception.
- `HttpApplicationError` (`shared`, abstract) — errors crossing an HTTP boundary; carries `HttpStatus` and renders itself as `ProblemDetail` (RFC 9457).
- Concrete errors grouped by domain via package/module placement and naming (upload errors in `api`, parsing errors in `rag`), each with a stable `code` used by BDD assertions.
- One `@RestControllerAdvice` per module with HTTP — never per endpoint.
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

## Dev Mode Entrypoint

`TestApplication` (in `src/test/`) runs the real `Application` with Testcontainers instead of Docker Compose:

```bash
./mvnw spring-boot:test-run
```

Caveat: Postgres/Neo4j test containers are disabled pending the Testcontainers 2.x migration ([0.2]).

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
