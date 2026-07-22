# Arquitetura — C4, Módulos e Fluxos

> Parte do [SDD](../sdd.md). Cobre: princípios de design, C4 (C1 contexto, C2 contêineres, C3 componentes por módulo), fluxos de runtime, tratamento de erros/logging e pontos de extensão. Modelos de dados detalhados em `dados.md`; segurança em `seguranca.md`.

---

## 1. Princípios de design

1. **Monólito modular (Spring Modulith).** Um JAR, cinco módulos (`api`, `rag`, `chat`, `mcp`, `shared`) com fronteiras verificadas em build (`ModularityTest` / `ApplicationModules.verify()`). O sistema simula uma arquitetura distribuída — eventos, filas, resiliência — sem pagar o custo operacional de microserviços.
2. **Pipeline orientado a eventos.** A ingestão é uma cadeia assíncrona de etapas (RF08); cada etapa consome um evento, faz uma coisa, persiste o resultado e publica o próximo evento. Nenhuma etapa chama a seguinte diretamente.
3. **Toda dependência externa atrás de uma porta.** Storage (`DocumentStorage`, ADR-001), antimalware, NER, parsing — interfaces do domínio com adaptadores para o serviço real. Onde o Spring AI já fornece a abstração (`ChatModel`, `EmbeddingModel`, `VectorStore`), ela **é** a porta — não criamos wrapper sobre wrapper.
4. **Multitenancy estrutural.** `tenantId`/`ownerId`/`isActive` fazem parte do modelo em todas as bases e de **todo** filtro de leitura — nunca são um `WHERE` opcional (RF30). A identidade chega por JWT (Keycloak) desde o dia 1 (ADL-008).
5. **Sem estado fora das bases.** A aplicação é stateless (consulta stateless por decisão — ADL-007); todo estado vive em Postgres/Neo4j/OpenSearch/Object Storage. Reiniciar a app no meio do pipeline não perde trabalho: o *event publication registry* do Modulith reentrega eventos incompletos.
6. **Design validado por BDD.** Cada componente descrito aqui existe para satisfazer cenários `@RFxx` — quando o desenho e o cenário divergirem, o cenário (derivado do requisito) vence.

---

## 2. C1 — Contexto do sistema

```mermaid
C4Context
    title C1 — Plataforma GraphRAG Local
    Person(usuario, "Usuário de tenant", "Envia documentos, acompanha status e faz perguntas")
    Person(admin, "Administrador", "Opera DLQ, fila de revisão de entidades e solicitações LGPD")
    System_Ext(agente, "Agente de IA externo", "LLM/agente que consome as ferramentas MCP")
    System(graphrag, "Plataforma GraphRAG", "Ingere documentos, constrói índice vetorial + grafo de conhecimento e responde perguntas com fundamentação e citação de fontes")
    System_Ext(keycloak, "Keycloak (IdP local)", "OAuth2/OIDC — emite os JWTs de usuários, admins e agentes")

    Rel(usuario, graphrag, "Upload, status, consulta", "HTTPS/JSON + JWT")
    Rel(admin, graphrag, "APIs administrativas", "HTTPS/JSON + JWT (roles)")
    Rel(agente, graphrag, "Busca híbrida e exploração de grafo", "MCP sobre HTTP + JWT")
    Rel(usuario, keycloak, "Obtém token", "OIDC")
    Rel(agente, keycloak, "Obtém token", "OIDC client credentials")
    Rel(graphrag, keycloak, "Valida assinatura e claims", "JWKS")
```

Três chamadores, um sistema. O agente MCP é um chamador de primeira classe (RF25): as mesmas garantias de isolamento multitenant valem para ele. O Keycloak é externo ao *sistema* mas interno ao *deployment* — roda no mesmo `compose.yaml`.

---

## 3. C2 — Contêineres

```mermaid
C4Container
    title C2 — Contêineres (deployment local via compose.yaml)
    Person(usuario, "Usuário / Admin / Agente MCP")

    System_Boundary(graphrag, "Plataforma GraphRAG") {
        Container(app, "Aplicação Spring Boot", "Java 25, Boot 4.1, Modulith", "Monólito modular: api, rag, chat, mcp, shared")
        ContainerDb(postgres, "PostgreSQL", "relacional", "Metadados, ciclo de vida, histórico, erros, auditoria, cotas + schema do Keycloak")
        ContainerDb(opensearch, "OpenSearch", "vetorial + full-text", "Chunks filhos: k-NN + BM25 + metadados de filtro")
        ContainerDb(neo4j, "Neo4j", "grafo", "Document, Chunk (openSearchId), Entity (aliases), ontologia fechada")
        Container(blobstore, "Object Storage", "MinIO + JuiceFS + Redis", "Originais (raw) e Markdown (transformed), montado como POSIX — ADR-001")
        Container(ollama, "Ollama", "LLM local", "qwen3:8b (chat/extração) + nomic-embed-text (embeddings)")
        Container(docling, "Docling Serve", "parsing", "PDF/imagem: layout, OCR, tabelas — ADR-002")
        Container(keycloak, "Keycloak", "IdP", "Realm graphrag versionado em JSON, importado no start — ADL-008")
        Container(lgtm, "Grafana OTel-LGTM", "observabilidade", "Traces, logs e métricas (aprofundamento postergado)")
        Container(gliner, "GLiNER (pendente)", "NER sidecar", "NER zero-shot pt-BR/EN, rótulos = ontologia — entra no Épico 6")
        Container(clamav, "ClamAV (pendente)", "antimalware", "Varredura no upload — entra no Épico 1")
        Container(nats, "NATS (pendente)", "mensageria", "Broker externo para fila/fair queueing — entra no Épico 3")
    }

    Rel(usuario, app, "REST / MCP", "HTTPS + JWT")
    Rel(app, keycloak, "JWKS", "HTTP")
    Rel(app, postgres, "JDBC/Flyway")
    Rel(app, opensearch, "REST (Spring AI VectorStore)")
    Rel(app, neo4j, "Bolt")
    Rel(app, blobstore, "POSIX (mount JuiceFS)")
    Rel(app, ollama, "HTTP (Spring AI)")
    Rel(app, docling, "HTTP")
    Rel(app, gliner, "HTTP")
    Rel(app, clamav, "TCP (clamd)")
    Rel(app, nats, "NATS protocol")
    Rel(app, lgtm, "OTLP push + scrape")
    Rel(keycloak, postgres, "JDBC (schema próprio)")
```

| Contêiner | Papel no design | Status |
|---|---|---|
| Aplicação Spring Boot | Único deployable; roda no host em dev (`spring-boot:run`/`test-run`) ou como imagem (`spring-boot:build-image`) | ✅ esqueleto |
| PostgreSQL | Fonte da verdade do ciclo de vida e de tudo que precisa de transação; também hospeda o schema do Keycloak (padrão de referência ADL-008) | ✅ container |
| OpenSearch | Índice dos **chunks filhos** (RF18): k-NN + BM25 na mesma engine | ✅ container |
| Neo4j | Grafo de conhecimento; ponte com o vetorial via `openSearchId` obrigatório (RF23) | ✅ container |
| Object Storage | MinIO atrás do JuiceFS: a app enxerga filesystem POSIX, o "bucket" é simulado com fidelidade (ADR-001) | ✅ containers (fricção de credenciais — [0.5]) |
| Ollama | Chat + embeddings residentes juntos (`OLLAMA_MAX_LOADED_MODELS=2`, ADR-003) | ✅ container |
| Docling Serve | Parsing pesado fora da JVM (ADR-002) | ✅ container |
| Keycloak | Última versão; build otimizado + realm JSON versionado; healthcheck no management port | ⏳ entra no Épico 0/1 (ADL-008) |
| GLiNER | Sidecar CPU de NER; contrato HTTP `texto + rótulos → entidades tipadas` | ⏳ Épico 6, spike + ADR (ADL-006) |
| ClamAV | `clamd` acessado no upload, antes de `UPLOADED` | ⏳ Épico 1 ([1.3]) |
| NATS | Substitui os eventos internos onde fila real/fair queueing forem necessários | ⏳ Épico 3 ([3.4]) |
| LGTM | Recebe OTLP; dashboards postergados | ✅ container (conflito de porta 3000 com a app — [0.3]) |

---

## 4. C3 — Componentes por módulo

### 4.1 Mapa de módulos e regras de dependência

```mermaid
flowchart LR
    subgraph app["Aplicação (JAR único)"]
        api["api<br/>entrada HTTP"]
        rag["rag<br/>pipeline + retrieval + geração"]
        mcp["mcp<br/>ferramentas para agentes"]
        chat["chat<br/>(placeholder — fora de escopo)"]
        shared["shared<br/>contratos transversais (SHARED)"]
    end
    api -- "API pública declarada<br/>(consulta e comandos, síncrono)" --> rag
    mcp -- "API pública declarada" --> rag
    api -. "eventos de domínio<br/>(pipeline, assíncrono)" .-> rag
    api --- shared
    rag --- shared
    mcp --- shared
    chat --- shared
```

Regras (verificadas por `ModularityTest`):

- `shared` é `ApplicationModule.Type.SHARED` — visível a todos.
- `api → rag` e `mcp → rag` são as **únicas** dependências diretas entre módulos, declaradas em `allowedDependencies`, e apenas para as interfaces públicas na raiz de `rag`. Justificativa: consulta e comandos (exclusão, reprocessamento) são síncronos por natureza — evento não serve para requisição/resposta.
- O **pipeline** é 100% por eventos: `api` publica, `rag` consome. `rag` não conhece `api`.
- Subpacotes `internal/` são invisíveis fora do módulo. Tudo que não precisa ser público é `internal`.
- `chat` permanece vazio (`package-info.java`), documentado como fora de escopo (ADL-007) — os pontos de extensão estão em `consulta.md`.

### 4.2 Módulo `api` — entrada HTTP

| Componente (indicativo) | Responsabilidade | RFs |
|---|---|---|
| `DocumentController` | `POST /api/v1/documents` (202 + id/status/`correlationId`), `GET /{id}/status`, `GET /{id}/history`, `DELETE /{id}` (soft delete), `POST /{id}/reprocess`, `POST /{id}/versions` | RF01, RF09, RF10 |
| `QueryController` | `POST /api/v1/query` — resposta síncrona completa (ADL-009) | RF25, RF26 |
| `internal/UploadValidationChain` | Validações em cadeia: tamanho (5MB), extensão × MIME real (Tika), nome/path traversal, vazio/corrompido, duplicidade por hash, malware (porta `MalwareScanner` → ClamAV). Cada validador rejeita com erro específico | RF02–RF04, RF07 |
| `internal/UploadService` | Orquestra o caminho síncrono: validação → hash → `DocumentStorage.store(RAW,...)` → linha em `documents` → transições `RECEIVED→VALIDATING→UPLOADED` → publica `DocumentUploadedEvent` | RF05–RF07 |
| `internal/AdminController` | Superfície administrativa (roles de admin): DLQ/reprocessamento, fila de revisão de entidades, solicitações LGPD | RF29, RF32, RF36 |
| `internal/ApiExceptionHandler` | `@RestControllerAdvice`: captura `HttpApplicationError` → `ProblemDetail` (RFC 9457) | RF02 |
| `CallerContext` (em `shared`) + resolver | Argument resolver que materializa `{tenantId, ownerId, roles}` das claims do JWT — controllers nunca leem token cru | RF30 |

### 4.3 Módulo `rag` — pipeline, retrieval e geração

O coração do sistema. Interfaces públicas na raiz (consumidas por `api`/`mcp`); todo o resto em `internal/`.

**API pública (raiz do módulo):**

| Interface (indicativa) | Operações | Consumidor |
|---|---|---|
| `RagQueryApi` | `answer(question, callerContext)` → resposta + citações; `search(question, callerContext)` → contexto híbrido bruto (para MCP) | `api`, `mcp` |
| `DocumentCommandApi` | `softDelete`, `reprocess`, `newVersion`, `lgpdErasure` — comandos síncronos sobre o ciclo de vida | `api` |

**Componentes internos, agrupados por área:**

```mermaid
flowchart TB
    subgraph lifecycle["Ciclo de vida"]
        LC["DocumentLifecycleService<br/>máquina de estados RF08 + histórico RF09<br/>agrega embeddingStatus/graphStatus"]
    end
    subgraph pipeline["Pipeline (listeners de evento, um por etapa)"]
        EX["ExtractionStep<br/>fila/cota (QUEUED) → DocumentReaderFactory<br/>(Docling | Tika | leitores dedicados) RF14/RF15"]
        TR["TransformationStep<br/>normalização Markdown + storage TRANSFORMED RF16/RF17"]
        CH["ChunkingStep<br/>hierárquico pai/filho RF18"]
        EMB["EmbeddingStep<br/>EmbeddingModel (Ollama) → VectorStore (OpenSearch) RF19/RF20"]
        GB["GraphBuildingStep<br/>GLiNER + ChatModel structured output → ontologia RF21/RF22"]
        ER["EntityResolutionService<br/>match determinístico → similaridade → merge/fila RF32"]
    end
    subgraph query["Consulta"]
        RET["HybridRetrievalService<br/>vetorial ‖ NER+travessia 1–2 hops → RRF k=60 RF25"]
        GEN["AnswerGenerationService<br/>chunks pai + delimitação anti-injection → citações RF26/RF34"]
    end
    subgraph jobs["Jobs em background"]
        GC["OrphanGcJob RF11"]
        REC["ReconciliationJob Neo4j↔OpenSearch RF38"]
        GS["GoldenSetEvaluator RF33"]
    end
    EX --> TR --> CH
    CH -- "evento fork" --> EMB
    CH -- "evento fork" --> GB
    GB --> ER
    EMB -- "sub-estado" --> LC
    GB -- "sub-estado" --> LC
    RET --> GEN
```

Notas de design:

- **Um listener por etapa**, cada um: consome evento → executa → persiste → registra transição via `DocumentLifecycleService` → publica o próximo evento. Falha em qualquer etapa cai no tratamento de `resiliencia-e-operacao.md` (status `*_FAILED`, retry, DLQ) sem afetar outros documentos (RF13/RF27).
- **Fork-join:** `ChunkingStep` publica **dois** eventos; `DocumentLifecycleService` agrega os sub-estados e deriva `COMPLETED`/`PARTIALLY_COMPLETED`/`FAILED` quando os dois ramos terminam (RF08).
- **Resiliência nas bordas:** chamadas a Ollama/GLiNER/Docling envolvidas por Resilience4j (circuit breaker + timeout, RF37) — o fallback é por etapa (detalhe em `resiliencia-e-operacao.md`).
- **Retrieval híbrido** roda as duas buscas **sempre em paralelo** (RF25); filtros `tenantId`/`ownerId`/`isActive` aplicados em ambas, sem caminho de código que os omita.

### 4.4 Módulo `shared` — contratos transversais

| Item | Papel |
|---|---|
| `ApplicationError` / `HttpApplicationError` | Hierarquia de erros; HTTP-facing sabe se renderizar como `ProblemDetail` |
| `Logger` / `LoggerFactory` / `internal/Slf4JLogger` | Logging centralizado, cache por classe — nunca `LoggerFactory.getLogger` do SLF4J direto |
| Eventos de domínio | `DocumentUploadedEvent`, `DocumentExtractedEvent`, `DocumentTransformedEvent`, `DocumentChunkedEvent`, `EmbeddingCompletedEvent`, `GraphBuildingCompletedEvent`, `Document*FailedEvent` — records imutáveis, todos carregam `documentId`, `tenantId`, `ownerId`, `correlationId` (contratos exatos em `dados.md`) |
| `DocumentStorage` (porta, ADR-001) | `store/retrieve/delete(stage, key)` com stages `RAW`/`TRANSFORMED`; usada por `api` (raw) e `rag` (transformed) — por isso vive em `shared` |
| `CallerContext` | Record `{tenantId, ownerId, roles}` — a identidade que atravessa módulos |

### 4.5 Módulo `mcp` — ferramentas para agentes

Expõe via `spring-ai-starter-mcp-server-webmvc` ferramentas que delegam para `RagQueryApi`: busca híbrida (contexto unificado topologia + texto) e exploração de entidade/vizinhança. Autenticação JWT idêntica à REST (client credentials para agentes — `seguranca.md`); o `CallerContext` do agente restringe tudo ao tenant dele. Nenhuma lógica de retrieval própria — o módulo é um adaptador de protocolo.

### 4.6 Módulo `chat` — placeholder

Vazio por decisão (ADL-007). A dependência `spring-ai-starter-model-chat-memory-repository-neo4j` sai do `pom.xml` até existir RF de conversação. Pontos de extensão descritos em `consulta.md` §extensões.

---

## 5. Fluxos de runtime

### 5.1 Ingestão (síncrono + assíncrono)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant API as api
    participant ST as DocumentStorage
    participant PG as PostgreSQL
    participant RAG as rag (listeners)
    participant OL as Ollama
    participant OS as OpenSearch
    participant NEO as Neo4j

    U->>API: POST /api/v1/documents (multipart + JWT)
    API->>API: valida JWT (JWKS) → CallerContext
    API->>API: validações RF02–RF04 + hash SHA-256 (RF07)
    API->>API: ClamAV scan (pendente Épico 1)
    API->>ST: store(RAW, /{tenant}/{user}/raw/...)
    API->>PG: insert documents + histórico (RECEIVED→VALIDATING→UPLOADED)
    API--)RAG: DocumentUploadedEvent (registry Modulith)
    API-->>U: 202 {id, status, correlationId}

    RAG->>RAG: cota/carga: >10MB acumulado → QUEUED (RF03)
    RAG->>RAG: EXTRACTING (Docling/Tika por MIME)
    RAG->>ST: store(TRANSFORMED, .../transformed/{fileId}/{v}.md)
    RAG->>PG: TRANSFORMING → CHUNKING + persiste chunks
    RAG--)RAG: fork: ChunksReadyForEmbedding ‖ ChunksReadyForGraph
    par ramo vetorial
        RAG->>OL: embeddings (chunks filhos)
        RAG->>OS: indexa vetores + metadados de filtro
    and ramo grafo
        RAG->>OL: extração estruturada (+ GLiNER)
        RAG->>NEO: Document/Chunk(openSearchId)/Entity + entity resolution
    end
    RAG->>PG: agrega sub-estados → COMPLETED | PARTIALLY_COMPLETED | FAILED
```

### 5.2 Consulta (síncrono, stateless)

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário / Agente MCP
    participant API as api | mcp
    participant RAG as rag (RagQueryApi)
    participant GL as GLiNER
    participant OS as OpenSearch
    participant NEO as Neo4j
    participant OL as Ollama

    U->>API: POST /api/v1/query {question} + JWT
    API->>RAG: answer(question, CallerContext)
    par busca vetorial
        RAG->>OS: top-N k-NN (filtros tenant/owner/isActive)
    and travessia de grafo
        RAG->>GL: NER da pergunta
        RAG->>NEO: travessia 1–2 hops → openSearchIds (filtros idem)
        RAG->>OS: busca por IDs (texto dos chunks)
    end
    RAG->>RAG: fusão RRF (k=60) → seleciona chunks pai
    RAG->>OL: prompt (contexto delimitado como dado, RF34)
    OL-->>RAG: resposta fundamentada
    RAG-->>API: {answer, citations[], metadata, correlationId}
    API-->>U: 200 JSON completo (ADL-009)
```

Se o circuito do Ollama estiver aberto (RF37): a consulta **degrada** — retorna os trechos recuperados sem geração, com indicação explícita, em vez de travar (detalhe em `resiliencia-e-operacao.md`).

---

## 6. Tratamento de erros e logging (amarração das convenções)

- **Erros de negócio** estendem `ApplicationError`; os que cruzam HTTP estendem `HttpApplicationError` e carregam `HttpStatus` + `toProblemDetail()`. Um `@RestControllerAdvice` por módulo com HTTP — nunca por endpoint.
- **Erros de pipeline** não viram exceção HTTP: viram status `*_FAILED` + linha em `processing_errors` (etapa, código, tentativa, diagnóstico JSONB, `correlationId` — RF28) + evento de falha. O chamador assíncrono não existe — o "retorno" é o estado consultável (RF09).
- **Logging** via `Logger` do `shared`; toda linha de log de pipeline inclui `documentId` e `correlationId`. O `correlationId` nasce no upload, viaja em todos os eventos e é correlacionado com o trace OTel existente.

---

## 7. Pontos de extensão (desenhados, não implementados)

| Extensão | Costura prevista | Gatilho para ativar |
|---|---|---|
| **SSE/streaming** (ADL-009) | `AnswerGenerationService` já separa *retrieval* de *geração*; um endpoint SSE novo consome o mesmo pipeline com `ChatModel.stream(...)`, emitindo evento terminal com citações | Existir um consumidor visual (UI) |
| **Conversação multi-turno** (ADL-007) | Contrato de sessão (`sessionId` opcional no request), camada de contexto conversacional entre `QueryController` e `RagQueryApi` (carrega histórico), reescrita de consulta (condensar histórico + pergunta antes do retrieval) — o retrieval e a geração atuais não mudam | RF novo de chat em `openspec/requirements/` |
| **NATS** (ADL-004) | Listeners de etapa consomem eventos Modulith hoje; a transição troca o transporte (binder/cliente NATS) mantendo os mesmos contratos de evento de `dados.md`; fair queueing por partição de tenant (RF39) | Épico 3 ([3.4]) |
| **Troca do modelo de embedding** (ADL-003) | `EmbeddingModel` é a porta; dimensão do índice OpenSearch parametrizada — troca exige reindexação completa (processo em `resiliencia-e-operacao.md`) | Golden set reprovar `nomic-embed-text` em pt-BR ([5.4]) |
| **Fallback do GLiNER** (ADL-006) | `NerClient` é porta; implementação alternativa delega ao `ChatModel` com structured output | Spike do GLiNER falhar |

---

## 8. Referências cruzadas

- Ciclo de vida, upload, eventos e fila: `ingestao.md` · Extração, chunking e embeddings: `extracao-e-vetorial.md` · Ontologia e grafo: `knowledge-graph.md` · Retrieval e geração: `consulta.md`
- Schemas e contratos de evento: `dados.md` · JWT/Keycloak, AuthZ e LGPD: `seguranca.md` · Falhas, DLQ, reconciliação e operação: `resiliencia-e-operacao.md` · BDD e avaliação: `qualidade-e-testes.md`
- Decisões: [ADL no índice](../sdd.md#4-architecture-decision-log-adl) · ADRs em [`../adr/`](../adr/)
