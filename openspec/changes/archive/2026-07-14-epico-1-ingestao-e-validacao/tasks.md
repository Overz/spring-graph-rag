# Tasks — Épico 1: Ingestão e Validação

> Grupos espelham as tarefas [1.x] do `docs/rag-plan.md` (ordem por dependência); detalhe técnico vem do `docs/sdd/ingestao.md`, `docs/sdd/dados.md` §2 e das decisões D1–D9 do `design.md`. DoD imutável: cenários `@RFxx` das features `ingestao/` passam sem `@pendente` + `ModularityTest` verde.

## 1. Migração V2 — cotas ([1.6] parcial)

- [x] 1.1 Criar `V2__tenant_quotas.sql` com a tabela `tenant_quotas` exatamente como no `dados.md` §2 (`tenant_id` PK, `max_storage_bytes`, `max_active_files`) — sem seed (D4: cota é opt-in)
- [x] 1.2 Verificar: Flyway aplica V1+V2 num banco limpo (boot no dev-mode)

## 2. Porta de storage ([1.5], ADR-001, D8)

- [x] 2.1 Criar a porta `DocumentStorage` no módulo `shared` (contrato: `store(stage, tenantId, ownerId, fileId, filename, content) → storageKey`; estágio `RAW` neste change) e o record de chave/resultado
- [x] 2.2 Implementar adaptador filesystem POSIX com base configurável `app.storage.base-dir` (dev: mount JuiceFS `./tmp/blobstore`; testes: diretório temporário) — gravação atômica (temp + move), chave `/{tenantId}/{userId}/raw/{fileId}/{filename}`
- [x] 2.3 Teste de unidade do adaptador: chave gerada, conteúdo byte a byte, rejeição de componentes de caminho inválidos

## 3. Persistência no `rag` ([1.6], D5, D6)

- [x] 3.1 Criar entidade/repositório de `documents` + gravação em `document_status_history` no módulo `rag` (internal), mapeando o schema V1 do Ép. 0
- [x] 3.2 Expor API pública `DocumentCommandApi` no `rag`: `registrarAceite(...)` (persiste a linha com status `UPLOADED` + histórico `RECEIVED → VALIDATING → UPLOADED` numa transação) e consultas de apoio à validação (existe duplicata por `(tenant, owner, hash)` com status ≠ `FAILED` — D7; uso corrente de storage/arquivos ativos do tenant)
- [x] 3.3 Declarar `allowedDependencies` `api → rag` restrito à interface pública e verificar `ModularityTest` verde

## 4. Endpoint de upload ([1.1])

- [x] 4.1 `POST /api/v1/documents` (multipart `file`) no módulo `api`: gera `correlationId` (RF28), resolve `tenantId`/`ownerId` do `CallerContext`, exige role `document:upload` (403 sem ela), responde `202` com `{id, status, correlationId, version}`
- [x] 4.2 Fluxo do aceite conforme `sdd/ingestao.md` §§2/4 e D9: multipart → arquivo temporário com SHA-256 em streaming → cadeia de validação (grupo 5) → `DocumentStorage.store(RAW,...)` → `DocumentCommandApi.registrarAceite(...)` (storage antes da linha)
- [x] 4.3 Erros concretos `HttpApplicationError` por rejeição + um `@RestControllerAdvice` do módulo `api` rendendo `ProblemDetail` RFC 9457 com `code` estável; logs de rejeição com motivo (sem linha em `documents` — `sdd/ingestao.md` §2)

## 5. Cadeia de validações ([1.2] + [1.4])

> Ordem e códigos: `sdd/ingestao.md` §2 + D3. Cadeia como lista ordenada de validadores (interface única), primeira falha responde.

- [x] 5.1 Validador de tamanho: > 5MB → `FILE_TOO_LARGE` (413), mensagem orienta pré-divisão (RF03); conferir `max-file-size: 6MB` do Ép. 0 segue coerente
- [x] 5.2 Validador de nome: vazio, > 255 chars, caracteres de controle, path traversal → `INVALID_FILENAME` (400) (RF02)
- [x] 5.3 Validador de vazio: 0 bytes → `EMPTY_FILE` (400) (RF02)
- [x] 5.4 Validador de tipo: MIME real por conteúdo via Tika; extensão e MIME na lista do RF04 e coerentes entre si → `UNSUPPORTED_FILE_TYPE` (415) / `MIME_MISMATCH` (415) (RF02/RF04)
- [x] 5.5 Validador de integridade estrutural (D3, roda após o tipo): PDF `%PDF`+`%%EOF`, magic bytes de JPG/PNG → `CORRUPTED_FILE` (400) (RF02)
- [x] 5.6 Validador de duplicidade (RF07, D7): mesmo `(tenant, owner, hash)` com status ≠ `FAILED` → `DUPLICATE_FILE` (409); outro usuário não bloqueia; constraint UNIQUE como guarda final de corrida
- [x] 5.7 Validador de cota (RF03 complemento, D4): linha em `tenant_quotas` presente e excedida (storage total ou arquivos ativos, uso derivado de `is_active = true`) → `QUOTA_EXCEEDED` (422); sem linha = sem limite
- [x] 5.8 Porta `MalwareScanner` (`api` internal, D1) + adaptador mock EICAR-aware (D2): assinatura EICAR → `MALWARE_DETECTED` (422), sem consumo de cota; comentário/registro do débito ClamAV ([1.3], épico futuro)

## 6. BDD — fechar as features de ingestão

- [x] 6.1 Automatizar as steps de `ingestao/upload.feature` (RF01, RF03, RF05, RF06) no harness E2E do Ép. 0 (`KeycloakTokens`, storage em diretório temporário, asserções em Postgres e filesystem; cenário RF01 valida `RECEIVED` pela primeira linha do histórico — D6)
- [x] 6.2 Automatizar as steps de `ingestao/validacao.feature` (RF02, RF04, RF07) — matriz de tipos com arquivos sintéticos válidos por formato (Tika precisa detectar o MIME real), EICAR literal no cenário de malware
- [x] 6.3 Remover `@pendente` de todos os cenários das duas features **exceto** "Reprocessamento explícito" (RF07 — permanece `@pendente` até existir pipeline/endpoint `/reprocess`); `./mvnw test` verde

## 7. Fechamento do change

- [x] 7.1 Atualizar `docs/sdd/ingestao.md` §2: linha do validador `CORRUPTED_FILE` e nota do ClamAV adiado (porta + mock, D1/D2/D3)
- [x] 7.2 Atualizar `docs/rag-plan.md` §5 (estado atual) e nota em [1.3]; atualizar `CLAUDE.md` (estágio de implementação)
- [x] 7.3 `./mvnw test` completo verde (`ModularityTest` + cenários ativos) + `graphify update .`
