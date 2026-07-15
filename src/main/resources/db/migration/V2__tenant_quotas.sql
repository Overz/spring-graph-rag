-- Cotas por tenant (RF03 complemento), conforme docs/sdd/dados.md §2.
-- Sem seed: cota é opt-in — tenant sem linha não tem limite (decisão D4 do change
-- epico-1-ingestao-e-validacao); o uso corrente é derivado dos documentos ativos.

CREATE TABLE tenant_quotas
(
  tenant_id         VARCHAR(100) PRIMARY KEY,
  max_storage_bytes BIGINT NOT NULL,
  max_active_files  INT    NOT NULL
);
