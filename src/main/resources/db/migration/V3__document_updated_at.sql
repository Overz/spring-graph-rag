-- RF40 (listagem de documentos): coluna de última atualização, ausente até aqui —
-- uploaded_at só marca a criação. Preenchida por padrão com uploaded_at para as linhas
-- já existentes; daí em diante, DocumentEntity a mantém via @PrePersist/@PreUpdate.

ALTER TABLE documents ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE documents SET updated_at = uploaded_at WHERE updated_at IS NULL;
ALTER TABLE documents ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE documents ALTER COLUMN updated_at SET DEFAULT now();
