# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**What it is:** a single-module Maven project implementing a local RAG (Retrieval-Augmented Generation) system, built as a **modular monolith** on Spring AI 2.0.0 + Spring Boot 4.1.0 + Java 25, with domain boundaries enforced by Spring Modulith.

**What it's for:** a learning project that simulates a real-world, multi-layered, observable, testable RAG architecture — 100% local and free, without relying on paid external APIs (see `docs/rag-plan.md` for the full product plan and rationale).

**What it does:** ingests documents, turns them into searchable vectors + full-text + a knowledge graph, and answers natural-language questions grounded in that content — citing sources and, where relevant, tracing multi-hop relationships between business entities.

> `docs/rag-plan.md` is the canonical backlog: 17 épicos, each with atomic tasks `[N.M]`, a Definition of Done, and the stack each task relies on. This file (`CLAUDE.md`) documents *what the architecture is and why*; it does not duplicate the backlog — check the plan for task-level detail and sequencing.

### Current implementation stage

> **Check `openspec status --change <name>` (or `openspec list`) for the authoritative, up-to-date task-level progress** — `openspec/changes/*/tasks.md` is the source of truth for what's done vs. pending per capability. This section is a narrative summary, kept accurate but coarser-grained.

The project has the full local infrastructure up and **Épico 1 (Ingestão de Documentos) implemented end to end**, inside the `foundation-and-ingestion-intake` OpenSpec change. Concretely today:

- Module skeleton (`api`, `chat`, `rag`, `mcp`, `shared`) has real code in `api` and `shared`; `chat`, `rag`, `mcp` are still just `package-info.java` with `@ApplicationModule` boundaries declared, verified by `ModularityTest`.
- `compose.yaml` provisions the full stack: Postgres, Neo4j, OpenSearch, MinIO+Redis+JuiceFS, Ollama, Docling Serve, and the Grafana/OTel LGTM observability stack.
- **Document upload (Épico 1) works end to end**: `POST /api/v1/documents` → MIME sniff (Tika) → SHA-256 dedup → raw file in cold storage (`DocumentStorage.store(RAW, ...)`) → `documents` row → `DocumentUploadedEvent` published. `GET /api/v1/documents/{id}/status` reads it back. See `com.github.overz.api` (public contract) and `com.github.overz.api.internal` (implementation).
- Project-wide code conventions are established and in use — see "Code Conventions" below: immutability (records + `@RequiredArgsConstructor` + `var`), the `ApplicationError`/`HttpApplicationError` hierarchy, and the `Logger`/`AppLogs` centralized logging.
- **Not yet implemented**: Épico 2 (document-parsing, module `rag`) — the listener for `DocumentUploadedEvent` doesn't exist yet, so uploaded documents stay at `PENDING` forever for now. `spring-ai-starter-model-ollama` is in `pom.xml` (added for embeddings, ADR-003), but no `ChatClient`/tool calling code exists yet — RAG generation and tool calling (Épicos 10/11) are still unimplemented.
- `mvnw`/`mvnw.cmd` wrapper scripts are missing from the repo (only `.mvn/wrapper/maven-wrapper.properties` is committed) — regenerate with `mvn -N wrapper:wrapper` before relying on `./mvnw` commands below.
- `TestcontainersConfiguration`'s Neo4j/Postgres beans are commented out: Testcontainers `2.0.5` made `Neo4jContainer`/`PostgreSQLContainer` non-generic, breaking the old `new Neo4jContainer<>(...)` pattern. Only the Grafana LGTM container bean is active; dev-mode (`spring-boot:test-run`) currently starts without a real Postgres/Neo4j until this is migrated to the new API.

## Build & Run Commands

```bash
# Build
./mvnw clean package

# Run tests (includes modularity verification)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ModularityTest

# Run in dev mode (Testcontainers instead of Docker Compose)
./mvnw spring-boot:test-run

# Build Docker image
./mvnw spring-boot:build-image
```

## Infrastructure

**What it is:** a set of local services — provisioned either via `compose.yaml` (Spring Boot Docker Compose support starts them automatically) or, for tests, via `TestcontainersConfiguration`.

**What it's for:** replacing every paid/external dependency (managed databases, hosted vector search, hosted LLM APIs) with an equivalent that runs entirely on the developer's machine.

| Service | What it is | What it's for | Status |
|---|---|---|---|
| `postgres:latest` | Relational database | Document metadata, pipeline status, tenant info (`knowledge_base`), migrated via Flyway | ✅ In `compose.yaml` |
| `neo4j:latest` | Graph database | Chat memory persistence today; will also hold the business-entity knowledge graph (Épico 17) | ✅ In `compose.yaml` (`neo4j/notverysecret`) |
| `grafana/otel-lgtm:latest` | Bundled observability stack (Grafana + Loki + Tempo + Mimir) | Traces, logs, and metrics for the whole ingestion/query journey | ✅ In `compose.yaml` (UI on port 3000) |
| OpenSearch | Vector + full-text search engine | Stores chunk embeddings (k-NN) and BM25 full-text in one engine, enabling hybrid search | ⏳ Dependency present (`spring-ai-starter-vector-store-opensearch`); container not yet in `compose.yaml` (Épico 7) |
| Ollama | Local LLM runtime | Serves the chat model (Qwen3, with tool calling) and the embedding model (`nomic-embed-text`), both resident together (`OLLAMA_MAX_LOADED_MODELS=2`) | ✅ In `compose.yaml`; `spring-ai-starter-model-ollama` dependency present (added for embeddings, ADR-003) — chat/tool-calling usage still not wired up (Épico 10/11) |
| MinIO | S3-compatible object storage | Keeps the original uploaded file (PDF/DOCX/etc.) separate from its processed text, simulating a real bucket | ⏳ Planned — no dependency yet (Épico 1.4) |

For tests, `TestcontainersConfiguration` is meant to spin up equivalent containers automatically (see the Neo4j/Postgres caveat above).

The app listens on port `3000` by default (env: `APP_PORT`, `APP_HOST`).

## Architecture

### Modular Structure (Spring Modulith)

**What it is:** one Spring Modulith module per top-level subpackage under `src/main/java/com/github/overz/`.

**What it's for:** keeping the codebase a single deployable JAR while still enforcing the same boundaries a microservice split would — no module reaches into another's internals, all cross-module talk goes through events or declared public APIs.

```
com.github.overz/
├── Application.java      ← @SpringBootApplication + @Modulithic
│
├── chat/                 ← What it does: conversational interface with the AI
│   └── package-info.java    (WebSocket/HTTP chat, chat memory)
│
├── rag/                  ← What it does: full RAG pipeline
│   └── package-info.java    (ingestion, embedding, vector store, retrieval, knowledge graph)
│
├── api/                  ← What it does: HTTP endpoints for external data ingestion
│   └── package-info.java    (file uploads, structured data, REST)
│
├── mcp/                  ← What it does: MCP server (tool provider for AI agents)
│   └── package-info.java    (tools/resources exposed via Model Context Protocol)
│
└── shared/               ← What it does: shared library (SHARED type — available to all modules)
    └── package-info.java    (value objects, events, interfaces, utilities)
```

### Module Dependency Rules

- `shared` → available to all modules implicitly (declared as `ApplicationModule.Type.SHARED`)
- `chat`, `rag`, `api`, `mcp` → **no direct cross-module imports allowed**
- Cross-module communication → via `ApplicationEventPublisher` (async domain events)
- Each module may expose public APIs (interfaces in the module root) that others can use

### Internal Packages

Any sub-package named `internal/` within a module is private to that module:

```
com.github.overz.rag/
├── RagService.java          ← public API of the module
└── internal/
    └── PdfProcessor.java    ← private implementation, invisible to other modules
```

### Modularity Test

**What it is:** `ModularityTest`, running `ApplicationModules.verify()` on every build (internally backed by ArchUnit via Spring Modulith — no separate ArchUnit dependency needed).

**What it's for:** making module boundary violations a compile-time-equivalent failure instead of a code-review judgment call.

**What it does:** fails the build if —

- Any module imports a class from another module's `internal/` package
- Any module imports from a module not listed in its `allowedDependencies`
- Circular dependencies exist between modules

It also generates architecture documentation (PlantUML diagrams) into `target/modulith-docs/`.

### Code Conventions

**What it is:** house rules for how new code in this repository is written — immutability, error hierarchy, logging.

**What it's for:** consistency across modules without re-deciding style on every class; each rule below applies to *all* code written in this project, not just the module that introduced it.

**Immutability**

- Data carriers (DTOs, value objects, domain events, request/response bodies) → `record`, always.
- A record that needs more than its canonical constructor (builder-style construction, JSON deserialization via builder) → add Lombok on top (`@Builder`, `@Jacksonized`) instead of hand-writing that logic.
- Services/components with injected dependencies → `private final` fields + Lombok `@RequiredArgsConstructor`. No field injection (`@Autowired` on a field), no setters, no mutable state after construction.
- Local variables → `var` (or `final var` when never reassigned) everywhere. Don't spell out the type when the compiler already knows it.

**Error Hierarchy (by Domain)**

- `ApplicationError` (`shared`, abstract, extends `RuntimeException`) — root of every exception the app throws on purpose.
- `HttpApplicationError` (`shared`, abstract, extends `ApplicationError`) — for errors that cross an HTTP boundary. Carries an `HttpStatus` and knows how to render itself as a `ProblemDetail` (RFC 9457) — no controller hand-builds an error body.
- Concrete errors are grouped by domain through **package/module placement and naming**, not through a forced shared middle class: e.g., upload-validation errors (`UnsupportedFileTypeError`, `EmptyFileError`, `DocumentNotFoundError`) live in `api`, parsing errors live in `rag` — each extends `HttpApplicationError` or `ApplicationError` directly, per whether it's HTTP-facing.
- A `@RestControllerAdvice` catches `HttpApplicationError` at the module's HTTP boundary and returns `error.toProblemDetail()` — one place per module, not per endpoint.

**Centralized Logging**

- `Logger` (`com.github.overz.shared`) — the interface every class logs through; `AppLogs.of(MyClass.class)` returns one, backed today by SLF4J (`Slf4JLogger`) but swappable without touching call sites.
- The factory caches by class (`ClassValue<Logger>`, key = `Class<?>`, value = the SLF4J `Logger` for it) — never call `LoggerFactory.getLogger(...)` directly in application code.
- Log messages must say what happened and why (`"upload rejected: unsupported content-type {}"`, not `"error"`) — a log line that doesn't help debug the actual failure isn't worth writing.

### Key Technology Decisions

**Spring AI RAG Pipeline**

| Concern | What it is | What it's for | Status |
|---|---|---|---|
| Chat model | Ollama serving `qwen3:8b` (or similar), with tool calling | Grounded answer generation (Épico 10) and agentic tool use (Épico 11) | ⏳ Dependency present (`spring-ai-starter-model-ollama`, added for embeddings — see below); no `ChatClient`/tool wiring yet (Épico 10/11) |
| Embeddings | Ollama serving `nomic-embed-text` (`spring-ai-starter-model-ollama`) | Turns text chunks into vectors, tuned for retrieval quality | ⏳ Dependency present; not yet wired into a pipeline. `OLLAMA_MAX_LOADED_MODELS=2` in `compose.yaml` keeps chat + embedding models resident together — see ADR-003 (also documents why this isn't `spring-ai-starter-model-transformers`, the original choice here) |
| Vector store | OpenSearch (`spring-ai-starter-vector-store-opensearch`) | Hybrid retrieval — k-NN vector search + BM25 full-text in the same engine | ⏳ Dependency present; container/index not yet provisioned |
| Document ingestion (PDF/image) | `DoclingPdfDocumentReader` (custom, calls the `docling-serve` HTTP service — see ADR-002) | Layout-aware extraction, OCR, table structure for PDFs and images | ✅ Implemented (`com.github.overz.rag.internal`); not yet wired into an upload pipeline |
| Document ingestion (other formats) | Apache Tika (`TikaDocumentReader`) | Extracts text from DOCX/PPTX/HTML/RTF; also used for MIME sniffing and native metadata | ⏳ Planned — no reader pipeline wired up yet |
| Chat memory | Neo4j (`spring-ai-starter-model-chat-memory-repository-neo4j`) | Persists conversation history across chat turns | ✅ Dependency present |
| MCP server | Exposed over HTTP (`spring-ai-starter-mcp-server-webmvc`) | Lets external AI agents call this app's tools/resources via Model Context Protocol | ✅ Dependency present; no tools registered yet |

**Spring Modulith**

| Artifact | What it's for |
|---|---|
| `spring-modulith-starter-core` | Module detection and event infrastructure |
| `spring-modulith-starter-jpa` | Domain event publication via JPA |
| `spring-modulith-starter-neo4j` | Domain event publication via Neo4j |
| `spring-modulith-starter-insight` | Module metadata via Actuator (`/actuator/modulith`) |

**Messaging**
Spring Cloud Stream + Spring Integration for async flows. Integration adapters cover HTTP, JPA, STOMP, and WebSocket.

**Observability**
OpenTelemetry traces exported to the LGTM stack; Prometheus metrics scraped by Grafana.

### Dev Mode Entrypoint

**What it is:** `TestApplication` (in `src/test/`).

**What it's for:** running the real `Application` locally without Docker Compose, replacing it with Testcontainers-managed infrastructure.

```bash
./mvnw spring-boot:test-run
```

See the Testcontainers caveat in "Current implementation stage" above — Postgres/Neo4j containers are currently disabled pending a Testcontainers 2.0 API migration.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
