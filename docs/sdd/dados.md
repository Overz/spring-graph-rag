# Modelo de Dados e Contratos de Evento

> Parte do [SDD](../sdd.md). Fonte de referência dos schemas (PostgreSQL, OpenSearch, Neo4j), do catálogo de eventos de domínio e das convenções de identificadores. Detalha e substitui o esboço da seção 7 do [`rag-plan.md`](../rag-plan.md).

---

## 1. Convenções de identificadores e chaves

| Identificador | Formato | Regra |
|---|---|---|
| `documentId` | UUID v4 | gerado no aceite do upload |
| `chunkId` | UUID v5 determinístico de `documentId:version:posPai[:posFilho]` | reprocessar gera os mesmos ids → indexação idempotente (upsert) |
| `openSearchId` (Neo4j) | = `chunkId` | mesmo valor; propriedade mantida por rastreabilidade ao RF23 |
| `correlationId` | UUID v4 | nasce no upload (RF28), viaja em todo evento/log/erro |
| chave de storage | `/{tenantId}/{userId}/{stage}/{fileId}/…` com `stage ∈ {raw, extracted, transformed}` | só a **chave** vai ao Postgres, nunca caminho de filesystem (ADR-001) |
| `entityId` | UUID v4 | identidade estável através de merges (aliases acumulam) |

## 2. PostgreSQL

Migrado por Flyway; a `V1` atual será **reescrita** com este schema ([0.4] — nenhum ambiente tem dado real).

```sql
CREATE TABLE documents
(
  id                      UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
  tenant_id               VARCHAR(100) NOT NULL,
  owner_id                VARCHAR(100) NOT NULL,                    -- = claim sub do JWT
  filename                VARCHAR(255) NOT NULL,
  extension               VARCHAR(20)  NOT NULL,
  content_type            VARCHAR(100) NOT NULL,                    -- MIME real (Tika)
  file_size_bytes         BIGINT       NOT NULL,
  file_hash_sha256        VARCHAR(64)  NOT NULL,
  raw_storage_key         VARCHAR(500) NOT NULL,
  extracted_storage_key   VARCHAR(500),                             -- artefato intermediário (RF27)
  transformed_storage_key VARCHAR(500),
  status                  VARCHAR(30)  NOT NULL,                    -- ciclo RF08 + *_FAILED (RF27)
  embedding_status        VARCHAR(20),                              -- PENDING|RUNNING|RETRYING|SUCCEEDED|FAILED
  graph_status            VARCHAR(20),                              -- idem
  version                 INT          NOT NULL DEFAULT 1,
  is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
  lgpd_redacted           BOOLEAN      NOT NULL DEFAULT FALSE,      -- RF36: chunks removidos por titular
  correlation_id          VARCHAR(100) NOT NULL,
  uploaded_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  completed_at            TIMESTAMPTZ,
  UNIQUE (tenant_id, owner_id, file_hash_sha256, version)           -- RF07
);
CREATE INDEX idx_documents_tenant_status ON documents (tenant_id, status);
CREATE INDEX idx_documents_queued ON documents (uploaded_at) WHERE status = 'QUEUED';

CREATE TABLE document_status_history
( -- RF09
  id          BIGSERIAL PRIMARY KEY,
  document_id UUID        NOT NULL REFERENCES documents (id),
  from_status VARCHAR(30),
  to_status   VARCHAR(30) NOT NULL,
  branch      VARCHAR(20),                                          -- EMBEDDING|GRAPH_BUILDING|NULL
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  detail      TEXT
);

CREATE TABLE chunks
( -- repositório autoritativo (retry de embedding + contexto de geração)
  id             UUID PRIMARY KEY,                                  -- chunkId determinístico
  document_id    UUID        NOT NULL REFERENCES documents (id),
  parent_id      UUID        REFERENCES chunks (id),                -- NULL = chunk pai
  position       INT         NOT NULL,
  content        TEXT        NOT NULL,
  token_count    INT         NOT NULL,
  injection_flag BOOLEAN     NOT NULL DEFAULT FALSE,                -- RF34: padrão suspeito sinalizado
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunks_document ON chunks (document_id);

CREATE TABLE processing_errors
( -- RF28
  id             BIGSERIAL PRIMARY KEY,
  document_id    UUID         NOT NULL REFERENCES documents (id),
  stage          VARCHAR(30)  NOT NULL,
  error_code     VARCHAR(50)  NOT NULL,
  message        TEXT,
  attempt        INT          NOT NULL,
  transient      BOOLEAN      NOT NULL,
  diagnostic     JSONB,
  correlation_id VARCHAR(100) NOT NULL,
  occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE dead_letter_events
( -- RF29 (pré-NATS; migra para stream DLQ do JetStream no Épico 3)
  id             BIGSERIAL PRIMARY KEY,
  document_id    UUID         NOT NULL,
  stage          VARCHAR(30)  NOT NULL,
  event_payload  JSONB        NOT NULL,
  attempts       INT          NOT NULL,
  last_error     TEXT,
  correlation_id VARCHAR(100) NOT NULL,
  dead_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
  reprocessed_at TIMESTAMPTZ,
  reprocessed_by VARCHAR(100)
);

CREATE TABLE tenant_quotas
( -- RF03 complemento
  tenant_id         VARCHAR(100) PRIMARY KEY,
  max_storage_bytes BIGINT NOT NULL,
  max_active_files  INT    NOT NULL
);

CREATE TABLE entity_review_queue
( -- RF32
  id               BIGSERIAL PRIMARY KEY,
  tenant_id        VARCHAR(100) NOT NULL,
  candidate_entity UUID         NOT NULL,                           -- nó provisório no Neo4j
  existing_entity  UUID         NOT NULL,
  similarity       NUMERIC(4,3) NOT NULL,
  status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',         -- PENDING|MERGED|KEPT_DISTINCT
  decided_by       VARCHAR(100),
  decided_at       TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_log
( -- RF31: append-only
  id             BIGSERIAL PRIMARY KEY,
  tenant_id      VARCHAR(100) NOT NULL,
  actor_id       VARCHAR(100) NOT NULL,
  action         VARCHAR(50)  NOT NULL,   -- UPLOAD|UPLOAD_REJECTED|SOFT_DELETE|REPROCESS|NEW_VERSION|ER_MERGE|LGPD_ERASURE|DLQ_REPROCESS|...
  target_type    VARCHAR(30)  NOT NULL,
  target_id      VARCHAR(100) NOT NULL,
  detail         JSONB,
  correlation_id VARCHAR(100),
  occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Imutabilidade (RF31): a role da aplicação recebe só INSERT/SELECT
-- + trigger BEFORE UPDATE OR DELETE que lança exceção (defesa dupla).
```

O *event publication registry* do Spring Modulith cria sua própria tabela (`event_publication`) via starter JPA — não modelamos manualmente.

## 3. OpenSearch — índice `chunks-v1` (alias `chunks`)

Só **chunks filhos**. Alias permite reindexação com troca atômica (troca de modelo de embedding → `chunks-v2`).

```json
{
  "settings": { "index": { "knn": true } },
  "mappings": {
    "properties": {
      "content":       { "type": "text" },
      "embedding":     { "type": "knn_vector", "dimension": 768,
                         "method": { "name": "hnsw", "space_type": "cosinesimil", "engine": "lucene" } },
      "chunkId":       { "type": "keyword" },
      "parentChunkId": { "type": "keyword" },
      "documentId":    { "type": "keyword" },
      "ownerId":       { "type": "keyword" },
      "tenantId":      { "type": "keyword" },
      "isActive":      { "type": "boolean" },
      "version":       { "type": "integer" }
    }
  }
}
```

- Metadados de filtro são `keyword` (filtro exato — `text` viraria busca analisada e vazaria silenciosamente).
- `content` analisado (standard) serve o BM25 — do híbrido (RF25) e do fallback de embedding indisponível.
- `dimension: 768` segue `nomic-embed-text`; parametrizado para a eventual troca ([5.4]).
- `_id` do documento OpenSearch = `chunkId` (upsert idempotente; reconciliação por id direto).

## 4. Neo4j — grafo de conhecimento

```cypher
// Unicidade e existência
CREATE CONSTRAINT document_id IF NOT EXISTS FOR (d:Document) REQUIRE d.id IS UNIQUE;
CREATE CONSTRAINT chunk_id    IF NOT EXISTS FOR (c:Chunk)    REQUIRE c.id IS UNIQUE;
CREATE CONSTRAINT chunk_os_id IF NOT EXISTS FOR (c:Chunk)    REQUIRE c.openSearchId IS NOT NULL; -- RF23
CREATE CONSTRAINT entity_id   IF NOT EXISTS FOR (e:Entity)   REQUIRE e.id IS UNIQUE;
CREATE CONSTRAINT entity_canonical IF NOT EXISTS
  FOR (e:Entity) REQUIRE (e.tenantId, e.canonicalName, e.type) IS UNIQUE;

// Índices de acesso
CREATE INDEX chunk_tenant_active IF NOT EXISTS FOR (c:Chunk)  ON (c.tenantId, c.isActive);
CREATE INDEX entity_tenant_type  IF NOT EXISTS FOR (e:Entity) ON (e.tenantId, e.type);

// Índice vetorial para entity resolution (RF32)
CREATE VECTOR INDEX entity_name_embedding IF NOT EXISTS
  FOR (e:Entity) ON (e.nameEmbedding)
  OPTIONS { indexConfig: { `vector.dimensions`: 768, `vector.similarity_function`: 'cosine' } };

// Nós
// (:Document {id, tenantId, ownerId, version, isActive})
// (:Chunk    {id, tenantId, openSearchId, isParent, position, isActive, injectionFlag})
// (:Entity   {id, tenantId, name, canonicalName, type, aliases: [..],
//             nameEmbedding: [..], confidence, provisional, schemaVersion})

// Estrutura
// (:Document)-[:HAS_CHUNK]->(:Chunk)
// (:Chunk)-[:CHILD_OF]->(:Chunk)                       // filho → pai (RF18)
// (:Chunk)-[:MENTIONS]->(:Entity)

// Conhecimento — ontologia fechada (RF21), versionada
// (:Entity)-[:USES|DEPENDS_ON|PART_OF|AUTHORED_BY|MENTIONS]->(:Entity)
// (:Entity)-[:RELATED_TO {description}]->(:Entity)     // escape
```

Nós de `Chunk` **não carregam conteúdo** (texto vive no Postgres/OpenSearch) — o grafo é topologia. Todo nó carrega `tenantId`; toda query é ancorada nele.

## 5. Catálogo de eventos de domínio

Records imutáveis em `shared`. **Envelope comum a todos:** `documentId`, `tenantId`, `ownerId`, `version`, `correlationId`, `occurredAt`.

| Evento | Publicado por | Consumido por | Payload além do envelope |
|---|---|---|---|
| `DocumentUploadedEvent` | `api` (upload aceito) | `rag` intake (decide `QUEUED` × `EXTRACTING`) | `rawStorageKey`, `contentType`, `fileSizeBytes` |
| `DocumentExtractedEvent` | ExtractionStep | TransformationStep | `extractedStorageKey`, `ocrApplied` |
| `DocumentTransformedEvent` | TransformationStep | ChunkingStep | `transformedStorageKey` |
| `ChunksReadyForEmbeddingEvent` | ChunkingStep (fork) | EmbeddingStep | `chunkCount` |
| `ChunksReadyForGraphBuildingEvent` | ChunkingStep (fork) | GraphBuildingStep | `chunkCount` |
| `EmbeddingCompletedEvent` | EmbeddingStep | LifecycleAggregator (join) | `outcome: SUCCEEDED\|FAILED` |
| `GraphBuildingCompletedEvent` | GraphBuildingStep | LifecycleAggregator (join) | `outcome: SUCCEEDED\|FAILED` |
| `StageFailedEvent` | qualquer etapa | registrador de erros + agendador de retry | `stage`, `errorCode`, `attempt`, `transient` |
| `SoftDeleteRequestedEvent` | `rag` (comando da API) | executores por base (relacional/vetorial/grafo) | — |

> **Débito conhecido (Épico 2, `openspec/changes/archive/epico-2-ciclo-de-vida`):** `SoftDeleteRequestedEvent`
> é desenho — a exclusão lógica (RF10) hoje é uma chamada síncrona em `DocumentLifecycleService`
> que atualiza Postgres/Neo4j/OpenSearch direto, sem publicar este evento (a infra de eventos
> internos do Épico 3 ainda não existe). Falha em Neo4j/OpenSearch durante a chamada síncrona
> vira linha em `processing_errors`, não retry automático — mitigação até a reconciliação do
> Épico 8 (RF38) existir. Migrar para o evento real é troca de mecanismo, não de contrato.

Regras:

- Publicação **na mesma transação** da mudança de estado (registry garante entrega at-least-once pós-commit).
- Os dois eventos do fork são publicados juntos, na transação que fecha `CHUNKING` — os ramos ficam independentes desde o nascimento (retry de um não reemite o outro).
- Consumo idempotente por guarda de estado + efeitos idempotentes (`ingestao.md` §7.2).
- Na migração para NATS (ADL-004), estes contratos viram mensagens serializadas (JSON) nos subjects — o catálogo não muda, muda o transporte.

## 6. Retenção e volumetria (escopo local)

- `document_status_history`, `processing_errors`, `audit_log` crescem sem limpeza automática nesta fase — volumes de estudo não justificam particionamento; `audit_log` **nunca** é expurgado (RF31).
- `chunks` de documentos inativos permanecem até o hard delete da versão (GC/LGPD) — são baratos e úteis para diagnóstico.
- Estimativa de referência: documento de 5MB ≈ 50–200 chunks filhos ≈ 200–800KB de vetores — mil documentos cabem folgadamente no OpenSearch local com heap de 512MB.
