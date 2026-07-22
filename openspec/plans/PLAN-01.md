# PLAN-01 — Ingestão e Validação (RF01–RF07)

> *Features:* `ingestao/upload.feature`, `ingestao/validacao.feature`
> *User story:* como usuário de um tenant, quero enviar arquivos com validação e deduplicação confiáveis, para que só conteúdo íntegro e inédito entre no pipeline.

> **Atualização (julho/2026): Épico 1 concluído** — change `openspec/changes/epico-1-ingestao-e-validacao`. RF01–RF07 implementados: `POST /api/v1/documents` (202, `correlationId`, role `document:upload`) com cadeia de 8 validações (tamanho → nome → vazio → MIME real/Tika → integridade estrutural `CORRUPTED_FILE` → duplicidade → cota → malware), storage RAW atrás da porta `DocumentStorage` (adaptador POSIX), persistência/histórico no `rag` via `DocumentCommandApi` (dependência `api → rag`), migração `V2__tenant_quotas.sql` (cota opt-in). **ClamAV ([1.3]) foi adiado por decisão de planejamento**: a porta `MalwareScanner` existe com mock EICAR-aware — a integração `clamd` real troca só o adaptador. Reenvio pós-`FAILED` entra como `version+1` (constraint RF07 preservada). Features `ingestao/` verdes, exceto o cenário de reprocessamento explícito (`@pendente` até existir pipeline/`/reprocess`).

**[1.1] Endpoint de upload** `[M · Must]` — `POST /api/v1/documents` (multipart), respondendo `202` com id + status; gera `correlationId` no ato do upload (RF01, RF28). Estados `RECEIVED → VALIDATING`.

**[1.2] Cadeia de validações** `[M · Must]` — tamanho máximo 5MB, extensão × MIME real (Tika, não extensão), nome de arquivo (inclusive path traversal), arquivo vazio/corrompido, duplicidade (RF02, RF04). Rejeição sempre informa o motivo; formatos aceitos: PDF, JPG, JPEG, PNG, CSV, JSON, XML, TXT, MD (DOCX/XLSX/PPTX ficam registrados como avaliação futura — RF04 complemento).

**[1.3] Varredura antimalware** `[M · Must]` — integrar ClamAV (novo serviço no `compose.yaml`) antes do status `UPLOADED`; falha gera rejeição `MALWARE_DETECTED` sem consumir cota de reprocessamento (RF02 complemento).
> **Nota (jul/2026, change epico-1):** adiado por decisão de planejamento. A porta `MalwareScanner` e a validação já existem (mock determinístico EICAR-aware, cenários BDD de malware verdes); esta tarefa passa a ser apenas o serviço ClamAV no `compose.yaml` + o adaptador `clamd`.

**[1.4] Idempotência por hash** `[P · Must]` — SHA-256 no upload; mesmo hash com sucesso anterior no mesmo tenant/usuário rejeita, salvo comando explícito de reprocessamento; hash igual de *outro* usuário não bloqueia (RF07).

**[1.5] Armazenamento do original** `[M · Must]` — `DocumentStorage` (reimplementar conforme ADR-001) gravando em `/{tenantId}/{userId}/raw/{fileId}/{filename}` (RF05); a chave retornada vai para o Postgres, nunca caminho cru.

**[1.6] Metadados e cotas** `[M · Must]` — linha em `documents` com todos os campos do RF06; cota por tenant (armazenamento/arquivos ativos) e teto de payload acumulado de 10MB que desvia para `QUEUED` (RF03 — o consumo controlado em si é do Épico 3).
