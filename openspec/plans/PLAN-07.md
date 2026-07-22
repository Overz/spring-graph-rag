# PLAN-07 — Consulta GraphRAG e MCP (RF25, RF26, RF33)

> *Features:* `consulta/recuperacao-hibrida.feature`, `consulta/geracao-resposta.feature`
> *User story:* como usuário (ou agente via MCP), quero respostas fundamentadas que combinem semântica e topologia, com fontes citadas — e quero que o sistema admita quando não sabe.

**[7.1] Retrieval híbrido paralelo** `[G · Must]` — busca vetorial top-N e, em paralelo, NER da consulta + travessia do grafo limitada a 1–2 hops a partir das entidades identificadas, resgatando os `openSearchId` dos chunks conectados (RF25).

**[7.2] Fusão RRF + filtros mandatórios** `[M · Must]` — Reciprocal Rank Fusion com `k = 60` como partida; filtros `ownerId`/`tenantId`/`isActive=true` aplicados em **todas** as buscas, sem exceção (RF25).

**[7.3] Ferramentas MCP** `[M · Must]` — expor a recuperação híbrida e a exploração de grafo como tools MCP (`spring-ai-starter-mcp-server-webmvc`), retornando contexto unificado (topologia + texto) para agentes externos (RF25).

**[7.4] Geração fundamentada** `[M · Must]` — prompt composto com os **chunks pai** dos filhos recuperados; resposta cita as fontes; sem contexto suficiente, o sistema admite não saber em vez de alucinar; conteúdo de documento sempre delimitado como dado, não instrução (RF26, RF34).

**[7.5] Golden set e avaliação** `[M · Must]` — conjunto de consultas de referência com chunks/entidades esperados, medindo precisão/recall e comparando GraphRAG (grafo+vetor) contra retrieval puramente vetorial (RF33) — é o que valida a arquitetura com dados e alimenta a [5.4].
