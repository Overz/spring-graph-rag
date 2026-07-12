-- Schema completo per docs/rag-plan.md seção 6.1 (ver ADR-001 para as colunas
-- *_storage_key/*_text_length). Só o Épico 1 (upload) popula colunas agora — o
-- restante fica NULL/default até os épicos correspondentes (3, 4) as usarem.
CREATE TABLE documents (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename                 VARCHAR(255) NOT NULL,
    content_type             VARCHAR(100),
    file_size_bytes          BIGINT,
    file_hash_sha256         VARCHAR(64) UNIQUE,
    raw_storage_key          VARCHAR(500),
    transformed_storage_key  VARCHAR(500),
    sanitized_storage_key    VARCHAR(500),
    extracted_text_length    INT,
    sanitized_text_length    INT,
    status                   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message            TEXT,
    category                 VARCHAR(100),
    tags                     TEXT[],
    language                 VARCHAR(10),
    author                   VARCHAR(255),
    source                   VARCHAR(255),
    confidentiality          VARCHAR(20) DEFAULT 'INTERNO',
    knowledge_base           VARCHAR(100) DEFAULT 'default',
    version                  INT DEFAULT 1,
    uploaded_at              TIMESTAMP NOT NULL DEFAULT now(),
    processed_at             TIMESTAMP
);
