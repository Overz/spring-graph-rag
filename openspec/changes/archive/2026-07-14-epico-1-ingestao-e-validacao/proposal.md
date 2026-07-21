# Épico 1 — Ingestão e Validação (RF01–RF07)

## Why

Com as fundações do Épico 0 prontas (build, schema V1, Keycloak/JWT, harness E2E), o sistema ainda não recebe nenhum arquivo — nenhum RF de domínio está implementado. Este change cobre o **Épico 1 do `docs/rag-plan.md`** (tarefas [1.1], [1.2], [1.4], [1.5], [1.6]): a porta de entrada do GraphRAG — upload validado, deduplicado e armazenado — da qual todos os épicos seguintes (ciclo de vida, eventos, extração) dependem.

## What Changes

- **[1.1] Endpoint de upload** — `POST /api/v1/documents` (multipart), `202 Accepted` com `{id, status, correlationId, version}`; `correlationId` gerado no ato do upload (RF01, RF28); rota exige role `document:upload` e resolve `tenantId`/`ownerId` só do `CallerContext` (Ép. 0).
- **[1.2] Cadeia de validações** (RF02, RF03, RF04) na ordem do `sdd/ingestao.md` §2 (barato → caro): tamanho ≤ 5MB → nome (path traversal, controle, comprimento) → vazio → integridade estrutural barata (**decisão deste change:** PDF/imagem truncados rejeitados com `CORRUPTED_FILE`) → extensão × MIME real via Tika → duplicidade → cota. Rejeição responde `ProblemDetail` (RFC 9457) com `code` estável e **não persiste linha** em `documents`.
- **[1.4] Idempotência por hash** (RF07) — SHA-256 em streaming; duplicata = mesmo `(tenant, owner, hash)` com sucesso anterior ou em processamento; `FAILED` anterior não bloqueia; outro usuário não bloqueia.
- **[1.5] Armazenamento do original** (RF05) — porta `DocumentStorage` (ADR-001) reimplementada; grava em `/{tenantId}/{userId}/raw/{fileId}/{filename}`; Postgres guarda a chave retornada; storage **antes** da linha.
- **[1.6] Metadados e cotas** (RF03 complemento, RF06) — linha em `documents` (schema V1 do Ép. 0) com transições `RECEIVED → VALIDATING → UPLOADED` gravadas no histórico; migração **V2** cria `tenant_quotas` (**decisão deste change:** sem linha = sem limite, cota opt-in).
- **Varredura de malware (RF02 complemento) — adiada com porta pronta:** o ClamAV ([1.3]) **não entra** neste change (decisão de planejamento: complexidade sem valor de estudo agora). Nasce a interface `MalwareScanner` na cadeia de validação com implementação mock (detecta apenas a assinatura EICAR; qualquer outro conteúdo = limpo), permitindo fechar os cenários BDD de malware sem o serviço real. Integração clamd real fica registrada como débito para épico futuro.
- **Fora de escopo (deferido):** eventos de ciclo de vida (RF12 → Ép. 3 [3.1]) — o documento para em `UPLOADED`; desvio `QUEUED` por payload acumulado > 10MB (RF03/RF13 → Ép. 3 [3.3], onde vive seu cenário BDD); endpoint `/reprocess` completo (o cenário RF07 de reprocessamento explícito permanece `@pendente` até existir pipeline a reexecutar).

## Capabilities

### New Capabilities

- `upload-de-documentos`: recebimento multipart autenticado, geração de `correlationId`, armazenamento do original segregado por tenant/usuário (RF05), persistência de metadados e histórico (RF06), limite individual de 5MB e cota por tenant (RF03). Cobre `ingestao/upload.feature`.
- `validacao-de-ingestao`: cadeia ordenada de validações com códigos estáveis — tipo suportado e MIME real por conteúdo (RF04), nome/vazio/integridade (RF02), varredura de malware via porta `MalwareScanner` (RF02 complemento, mock EICAR), idempotência por hash com escopo tenant+owner (RF07). Cobre `ingestao/validacao.feature`.

### Modified Capabilities

Nenhuma — `autenticacao` (Ép. 0) não muda de requisito; o novo endpoint apenas a consome (role `document:upload`).

## Impact

- **Código:** módulo `api` (controller de upload, validadores, `@RestControllerAdvice`, erros concretos `HttpApplicationError`); módulo `api` (controller, validadores — incluindo a porta `MalwareScanner` internal — e advice); módulo `rag` (persistência de `documents`/histórico via API pública consumida pelo `api` — fatia mínima do lifecycle); módulo `shared` (porta `DocumentStorage` reimplementada, ADR-001).
- **Banco:** migração `V2__tenant_quotas.sql`.
- **Config:** `application.yaml` (diretório do storage JuiceFS, cotas nada — opt-in).
- **Docs:** `docs/sdd/ingestao.md` §2 atualizado (validador `CORRUPTED_FILE`; ClamAV adiado com porta + mock) — regra de coerência: repositório vence, docs acompanham.
- **BDD:** `ingestao/upload.feature` e `ingestao/validacao.feature` saem de `@pendente`, **exceto** o cenário RF07 de reprocessamento explícito.
- **Sem impacto:** compose.yaml (ClamAV não entra), OpenSearch/Neo4j (épicos 5–6), eventos Modulith (Ép. 3).
