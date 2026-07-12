# Resiliência e Operação

> Parte do [SDD](../sdd.md). Cobre falha por etapa e recuperação parcial (RF27), registro estruturado de erros (RF28), retry e DLQ (RF29), circuit breaker (RF37), reconciliação entre bases (RF38), backup (RNF03) e a operação local do sistema.
> **Features BDD:** `resiliencia/falha-e-recuperacao.feature`, `resiliencia/retry-dlq.feature`, `resiliencia/circuit-breaker.feature`, `resiliencia/reconciliacao.feature`.

---

## 1. Falha por etapa e recuperação parcial (RF27)

Cada etapa tem status de falha próprio e artefato persistido — o retry retoma **da etapa falhada**, nunca do zero:

| Etapa falhada | Status | Artefato que o retry reaproveita |
|---|---|---|
| `EXTRACTING` | `EXTRACTION_FAILED` | original no storage (`raw`) |
| `TRANSFORMING` | `TRANSFORMATION_FAILED` | extração persistida (`extracted` — por isso o estágio intermediário existe) |
| `CHUNKING` | `CHUNKING_FAILED` | Markdown transformado (`transformed`) |
| `EMBEDDING` (ramo) | `embedding_status = FAILED` | chunks na tabela `chunks` |
| `GRAPH_BUILDING` (ramo) | `graph_status = FAILED` | chunks + entidades já mescladas (ER é idempotente por `MERGE`) |

- Falha em um **ramo** do fork não afeta o outro — sub-estados independentes; o agregado vira `PARTIALLY_COMPLETED` quando um ramo conclui e o outro esgota (RF08).
- Falha em um **documento** não afeta os demais — transação e retry por documento (RF13); não há processamento em lote entre documentos.

## 2. Classificação de erros e registro estruturado (RF28)

- Taxonomia binária na origem: exceções de etapa são `TransientStageError` (timeout, 429, indisponibilidade momentânea, circuito aberto) ou `PermanentStageError` (arquivo corrompido, conteúdo inextraível, resposta estruturalmente inválida após N tentativas). Quem lança **decide** a classe — o mecanismo de retry não adivinha.
- Todo erro grava linha em `processing_errors`: etapa, código, mensagem, tentativa, `transient`, payload de diagnóstico (JSONB — request/response truncados, ids externos), `correlationId`, timestamp.
- `correlationId` nasce no upload e aparece em **todo** evento, log e registro de erro (RF28); o trace OTel existente carrega o mesmo id como atributo — correlação log↔trace de graça.

## 3. Retry e DLQ (RF29)

### 3.1 Retry (falhas transitórias)

- **Dirigido por banco, não por memória:** um agendador varre documentos em status `*_FAILED` (ou sub-estado `FAILED` com `transient = true` no último erro) com `attempt < max`, e re-publica o evento da etapa. Sobrevive a restart (estado no Postgres, não em fila em memória) e é consistente com o registry do Modulith.
- Backoff exponencial: base 2s, fator 2, teto 5 min; `max attempts` default **5**, configurável por etapa (`app.pipeline.retry.*`). Durante a espera o sub-estado mostra `RETRYING`.

### 3.2 DLQ (falhas definitivas)

- Esgotadas as tentativas — ou `PermanentStageError` de imediato (corrompido não melhora com retry) — o evento vai para `dead_letter_events` (payload completo, tentativas, último erro) e o documento para `FAILED` (ou o sub-estado do ramo).
- **Reprocessamento manual** restrito (role `admin:dlq`): API admin lista a DLQ (por tenant), permite reprocessar (re-publica o evento a partir do payload guardado, zera contagem, audita `DLQ_REPROCESS`) ou descartar (auditado).
- Na migração para NATS (ADL-004): JetStream com *max deliveries* → subject de DLQ; a tabela vira projeção de leitura/administração — a API admin não muda.

## 4. Circuit breaker (RF37)

Resilience4j (entra no `pom.xml` no Épico 8 — [8.4]) em torno de **toda** chamada a serviço externo. Distinto do retry: trata degradação **sustentada** (evita esgotar threads/conexões em chamadas penduradas), não falha pontual.

| Instância | Timeout por chamada | Fallback quando aberto |
|---|---|---|
| `ollama-chat` (extração RF21, geração RF26) | 60s | pipeline: reenfileira o ramo (`RETRYING` adiado); consulta: resposta degradada sem geração (`degraded: true` — `consulta.md` §5) |
| `ollama-embedding` (RF19, ER, consulta) | 15s | pipeline: reenfileira; consulta: fallback BM25 (`consulta.md` §5) |
| `docling` (RF15) | 120s | reenfileira extração (sem alternativa local a OCR) |
| `gliner` (RF21, NER de consulta) | 10s | pipeline: extração só-LLM (ADL-006); consulta: ramo de grafo vazio |
| `clamav` (RF02) | 30s | **fail-closed**: upload rejeitado com `503` — aceitar arquivo sem varredura viola o RF02; indisponibilidade de antivírus não pode virar bypass |

Config default: janela deslizante de 20 chamadas, abre a 50% de falha, half-open após 30s — tudo em `application.yaml`, ajustável sem código. Métricas do Resilience4j já saem pelo Micrometer/OTel existente (aprofundamento no Épico 10).

## 5. Reconciliação Neo4j ↔ OpenSearch (RF38)

Job periódico (default: a cada 6h, configurável), por tenant, em lotes:

1. **Chunk ativo sem vetor:** `Chunk {isActive: true}` no Neo4j cujo `openSearchId` não existe no índice → sinaliza para reindexação (re-publica embedding do chunk a partir da tabela `chunks`) + registra divergência.
2. **Vetor órfão:** documento no OpenSearch sem `Chunk` ativo correspondente no Neo4j → remove o vetor + registra.
3. Toda divergência gera linha em `processing_errors` (código `RECONCILIATION_DIVERGENCE`) — divergência recorrente é sintoma de bug de escrita, não ruído a ignorar.

**Prevenção (avaliação do outbox, RF38):** o event publication registry já dá publicação transacional (metade do problema); as escritas duplas dentro dos ramos (ex.: OpenSearch + Postgres no embedding) permanecem não atômicas — o `chunkId` determinístico torna a autocorreção segura (upsert/delete por id). Outbox completo fica **descartado nesta fase**: a reconciliação + idempotência cobre o risco no volume local; reavaliar com NATS.

## 6. Backup e recuperação (RNF03)

Metas de RPO/RTO ficam para o Épico 10 (postergação da `sdd.md` premissa 4); o **mecanismo** fica definido já:

- **Conjunto primário (não reconstruível):** Postgres (`pg_dump`) + Object Storage (bucket MinIO / diretório JuiceFS) — metadados, auditoria e originais. Com eles, **tudo** o mais é reconstruível por reprocessamento.
- **Conjunto derivado (reconstruível, caro):** Neo4j (`neo4j-admin database dump`) e OpenSearch (snapshot repository em volume local) — backup recomendado para evitar reprocessamento em massa, mas a perda não é fatal.
- Scripts de dump/restore versionados em `infra/backup/` (a criar no Épico 8/10), operando sobre os volumes do compose com os serviços pausados — suficiente e honesto para o escopo local.

## 7. Runbooks de operação local

| Situação | Procedimento |
|---|---|
| **Troca de modelo de embedding** ([5.4]) | criar `chunks-v2` com a nova dimensão → reprocessar ramo `EMBEDDING` de todos os documentos ativos (evento em massa, fila controlada) → validar contagens (reconciliação) → trocar alias `chunks` atomicamente → remover `chunks-v1`. ADR registrando o novo modelo |
| **Mudança de ontologia** (RF21) | ver `knowledge-graph.md` §1 — migração Cypher mapeável ou reprocessamento do ramo `GRAPH_BUILDING` |
| **Restart no meio do pipeline** | nenhuma ação: registry reemite publicações incompletas; guardas de estado descartam duplicatas |
| **OpenSearch não sobe (Linux)** | `sudo sysctl -w vm.max_map_count=262144` (documentado no `compose.yaml`) |
| **Reprocessamento manual de documento** | `POST /{id}/reprocess` (dono) ou API de DLQ (admin) — retoma da etapa falhada |
| **Divergência recorrente na reconciliação** | tratar como bug de escrita: investigar `processing_errors` por `correlationId`, não silenciar o job |

## 8. Decisões registradas nesta seção

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Retry dirigido por banco (agendador sobre status) | retry em memória (Spring Retry puro) | sobrevive a restart; estado observável via RF09; consistente com o registry |
| DLQ como tabela + API admin (pré-NATS) | esperar o broker para ter DLQ | RF29 é Must antes do Épico 3; a API admin sobrevive à migração |
| ClamAV **fail-closed** | fail-open (aceitar sem varredura) | indisponibilidade de antivírus não pode virar bypass de segurança |
| Outbox completo descartado nesta fase | implementar outbox nas escritas duplas | ids determinísticos + reconciliação cobrem o risco local; complexidade adiada com critério de reavaliação (NATS) |
| Backup: primário = Postgres + storage; derivado = Neo4j/OpenSearch | tratar as 4 bases como iguais | hierarquia de reconstrutibilidade real; prioriza o que não se recupera |
