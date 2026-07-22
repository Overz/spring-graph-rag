# PLAN-09 — Segurança e Conformidade (RF30, RF31, RF34–RF36)

> *Features:* `seguranca/multitenant.feature`, `seguranca/auditoria.feature`, `seguranca/prompt-injection.feature`, `seguranca/autenticacao-e-criptografia.feature`, `seguranca/lgpd.feature`
> *User story:* como responsável pela plataforma, quero isolamento absoluto entre tenants, trilha de auditoria imutável e conformidade legal — sem que conteúdo malicioso em documentos manipule a IA.

**[9.1] Isolamento multitenant (AuthZ)** `[G · Must]` — validação estrita de `userId`/`tenantId` em upload, consulta, exclusão e reprocessamento (RF30). **Nota de sequência:** o isolamento de *dados* (filtros/modelo) nasce nos épicos 1–7; esta tarefa consolida a camada de autorização em cima — não deixe para cá o que é estrutural lá.

**[9.2] Auditoria imutável** `[M · Should]` — log imutável de toda ação de estado (envios, deleções lógicas, reprocessamentos, mudanças críticas); tentativa de adulteração é rejeitada e registrada (RF31).

**[9.3] Prompt injection** `[M · Should]` — conteúdo de documento sempre delimitado como dado nos prompts (RF21, RF26); saída da extração validada contra o schema fechado; chunks com padrão suspeito são sinalizados para revisão sem bloquear o pipeline (RF34 — sinalização, não prevenção absoluta; ref. OWASP LLM01).

**[9.4] Endurecer AuthN/AuthZ e formalizar a ADR do Keycloak** `[M · Should]` — a base JWT+Keycloak nasce no Épico 0 ([0.7], ADL-008); aqui entram: a ADR formal da decisão, a cobertura completa dos cenários de `autenticacao-e-criptografia.feature` (MCP via client credentials, expiração de token, roles em todas as rotas admin) e TLS na borda via proxy (RF35). **Feito (`openspec/changes/archive/2026-07-20-auth-phantom-token`, `openspec/decisions/ADR-004.md`):** login de usuário final ganhou `login`/`refresh`/`logout` via token opaco (Phantom Token pattern, Redis) em vez do JWT cru do Keycloak, via novo pacote `api/internal/auth/`. Cenários `@RF35` correspondentes verdes em `autenticacao-e-criptografia.feature`.

**[9.5] Criptografia** `[M · Could]` — TLS em trânsito e criptografia em repouso para Object Storage, PostgreSQL, Neo4j e OpenSearch (RF35); no escopo local, documentar o que é real e o que é simulado.

**[9.6] LGPD — direito ao esquecimento** `[G · Could]` — localizar todos os nós/relacionamentos/chunks/vetores de um titular (`PERSON`), executar hard delete **síncrono e verificável** (independente do GC do RF11) e registrar na auditoria do RF31 (RF36).
