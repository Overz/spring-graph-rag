-- Baseline do schema de documentos, conforme docs/sdd/dados.md §2 (RF06/RF08/RF09).
-- Contém apenas as tabelas do ciclo de vida de documentos; as demais tabelas
-- (chunks, dead_letter_events, tenant_quotas, entity_review_queue, audit_log)
-- entram nas migrações dos épicos que as usam.

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
