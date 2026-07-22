# PLAN-08 — Resiliência (RF27–RF29, RF37, RF38)

> *Features:* `resiliencia/falha-e-recuperacao.feature`, `resiliencia/retry-dlq.feature`, `resiliencia/circuit-breaker.feature`, `resiliencia/reconciliacao.feature`
> *User story:* como operador, quero que falhas sejam isoladas por etapa, recuperáveis do ponto onde pararam, e que as bases nunca divirjam silenciosamente.

**[8.1] Falha isolada por etapa + recuperação parcial** `[G · Must]` — cada fase com status de falha próprio (`EXTRACTION_FAILED`, ..., `GRAPH_BUILDING_FAILED`); reprocessamento retoma da última etapa concluída, aproveitando artefatos já persistidos (chunks salvos, etc.) (RF27).

**[8.2] Registro estruturado de erros + rastreabilidade** `[M · Must]` — todo erro registra etapa, código, mensagem, timestamp, tentativa e payload de diagnóstico (RF28); `correlationId` do upload presente em todos os eventos e logs de todas as etapas (a instrumentação OTel já existente carrega o trace).

**[8.3] Retry + DLQ** `[M · Must]` — falhas transitórias (ex.: rate limit) com retentativas automáticas e backoff; falhas definitivas roteadas para DLQ com status `FAILED` e reprocessamento manual restrito a usuários autorizados (RF29).

**[8.4] Circuit breaker nas chamadas de IA** `[M · Should]` — Resilience4j em torno de LLM/embedding (RF19, RF21, RF26): circuito aberto → fallback por etapa — embedding reenfileira para depois; consulta degrada para resposta só-vetorial em vez de travar (RF37). Distinto do retry: trata degradação sustentada, não falha pontual.

**[8.5] Reconciliação Neo4j ↔ OpenSearch** `[M · Should]` — job periódico verifica que todo `Chunk` ativo tem vetor correspondente e vice-versa; vetor órfão é removido, chunk sem vetor é sinalizado para reindexação, divergências registradas para auditoria; avaliar padrão outbox como mitigação preventiva (RF38).
