# PLAN-06 — Knowledge Graph (RF21–RF24, RF32)

> *Features:* `knowledge-graph/extracao-entidades.feature`, `knowledge-graph/construcao-grafo.feature`, `knowledge-graph/entity-resolution.feature`
> *User story:* como plataforma GraphRAG, quero um grafo de entidades limpo — ontologia fechada, sem duplicatas, sempre ancorado ao tenant — para responder o que a similaridade sozinha não alcança.

**[6.1] Extração híbrida com ontologia fechada** `[G · Must]` — NER rápido via **GLiNER** (sidecar CPU zero-shot, rótulos = a própria ontologia; spike + ADR no início do épico — ADL-006, fallback: extração só-LLM) para entidades triviais + LLM com *structured output* para relacionamentos complexos, restritos a schema fechado: entidades `PERSON`, `ORGANIZATION`, `PRODUCT`, `TECHNOLOGY`, `LOCATION`, `DATE`, `CONCEPT`; relacionamentos `USES`, `DEPENDS_ON`, `PART_OF`, `AUTHORED_BY`, `MENTIONS` + escape `RELATED_TO` com propriedade descritiva. Resposta fora do schema é descartada sem interromper o pipeline; o schema é **versionado**, com estratégia de migração para mudanças (RF21).

**[6.2] Construção do grafo** `[M · Must]` — nós `Document`, `Chunk`, `Entity` e relacionamentos no Neo4j (RF22), sempre ancorados ao tenant.

**[6.3] `openSearchId` obrigatório** `[P · Must]` — todo nó `Chunk` carrega o `openSearchId` do vetor correspondente; escrita sem ele é rejeitada (RF23) — é a ponte vetor↔grafo que o retrieval (RF25) e a reconciliação (RF38) usam.

**[6.4] Entity resolution** `[G · Must]` — antes de persistir `Entity` nova: (1) match determinístico por nome normalizado + tipo no tenant; (2) similaridade de embedding do nome+contexto com merge automático acima do limiar de confiança, fila de revisão manual na faixa intermediária, nó novo abaixo dela; aliases preservados no nó mesclado; **nunca** mescla entre tenants distintos (RF32).

**[6.5] Escopo global × local** `[M · Should]` — agrupamentos/comunidades orientados por `tenantId`; consultas restritas validam `ownerId` (RF24).
