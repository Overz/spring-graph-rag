# Design — Épico 0: Fundação e Débitos de Ambiente

## Context

O repositório tem infra completa no `compose.yaml` e suíte BDD pronta, mas o código de domínio foi resetado e sete débitos de ambiente bloqueiam o início do Épico 1 (todos verificados no repositório na abertura deste change). O design de referência já existe e **não é repetido aqui**: identidade em `docs/sdd/seguranca.md` §1 (ADL-008), schema em `docs/sdd/dados.md` §2. Este documento registra apenas as decisões **novas**, tomadas com o usuário na abertura deste change.

**Ordem de autoridade dos documentos** (acordada com o usuário): `requisitos.md` (RF/RNF) → BDD → SDD. O `rag-plan.md` é o documento **mais antigo** da cadeia — vale para ordenação de épicos/tarefas `[N.M]`, mas todo detalhe técnico dele (schemas, DoDs, caminhos) é conferido contra o SDD e o repositório antes de entrar num artefato.

## Goals / Non-Goals

**Goals:**

- `./mvnw clean compile` funciona sem Maven global; `./mvnw spring-boot:test-run` sobe com Testcontainers.
- `docker compose up` + app local sobem juntos sem conflito de porta.
- Schema Flyway V1 alinhado a RF06/RF08/RF09 (DoD da [0.4]).
- `juicefs-format` executa com sucesso; mount gravável pela app.
- Keycloak no ar com realm `graphrag` versionado; app como resource server; `CallerContext` das claims.
- **Harness E2E/BDD operante** (Testcontainers + Cucumber-Spring + tokens reais do Keycloak de teste), com os cenários `@RF35` de token verdes — o Épico 0 é pavimentação, e a pavimentação inclui a fundação de testes que todos os épicos seguintes usarão.

**Non-Goals:**

- Nenhum RF de negócio (upload, pipeline etc.) — Épico 1 em diante.
- `tenant_quotas` (migração do Épico 1) e `audit_log` (Épico 9).
- Cobertura completa de `autenticacao-e-criptografia.feature` — AuthN de MCP, TLS e criptografia são [9.4]/[9.5].
- Endurecimento/ADR formal do Keycloak (fica para [9.4], como o plano prevê).

## Decisions

1. **Portas — app move, infra fica no default.** `APP_PORT` default: `3000 → 8090`. Serviços do compose nas portas default de cada produto: Grafana `3000`, Keycloak `8080`; Adminer (indiferente) remapeado `8080 → 8081` para liberar a default do Keycloak. Alternativas descartadas: app em 8080 (colide com a convenção de o compose usar defaults) e remapear o Grafana (contraria a mesma convenção).
2. **Flyway V1 core.** V1 = `documents` + `document_status_history` + `processing_errors`, com o schema de `docs/sdd/dados.md` §2 (que detalha e substitui a §7.1 do rag-plan — inclui `extracted_storage_key`, `lgpd_redacted`, `branch`, `transient`, `TIMESTAMPTZ` e índices). Cada migração nasce junto do código que a usa: `tenant_quotas` → Épico 1, `chunks` → Épico 5, `entity_review_queue` → Épico 6, `dead_letter_events` → Épico 8, `audit_log` (append-only, permissão+trigger) → Épico 9. Alternativa descartada: V1 completa — anteciparia design de tabelas sem código nem DoD para validá-las.
3. **Keycloak com database dedicado na instância Postgres existente**, provisionado por script de init no padrão do projeto de referência do usuário (`bookstore.com`): `infra/postgres/configure.sh` + `infra/postgres/.env.postgres` com `POSTGRES_MULTIPLE_DATABASE_N=db/schema/user/pass` (entrada: `keycloak/keycloak/keycloak/keycloak`). Alternativas descartadas: schema no database da app (mistura Flyway com tabelas do Keycloak) e dev-mode/H2 (diverge da ADL-008).
4. **Credenciais de storage num único lugar:** `infra/minio/.env.minio` com o par usado por MinIO, `juicefs-format` e `juicefs-mount` via `env_file`/variáveis — elimina a classe de erro, não só a instância atual.
5. **Multipart `max-file-size: 6MB`** (era 20MB) — pouco acima do limite de negócio de 5MB do RF03, para que a validação de domínio (413 + `code` estável) responda antes da exceção genérica do container (decisão herdada de `docs/sdd/ingestao.md` §2, aplicada aqui).
6. **Nome da migração coerente com o conteúdo:** `V1__baseline_documents.sql` (a atual `V1__create_documents_table.sql` sai — reescrita, não incremento, pois nenhum ambiente tem dado real).
7. **Fundação E2E/BDD nasce neste épico** (pedido do usuário, alinhado ao SDD `qualidade-e-testes.md` §§2–3): harness `@CucumberContextConfiguration` + `@SpringBootTest(RANDOM_PORT)` importando a `TestcontainersConfiguration`; Keycloak de teste importa o **mesmo** realm JSON do compose; cache de token por usuário. Os cenários `@RF35` de token fecham já — o `401` vem do filter chain do Spring Security, antes do roteamento, então não dependem de endpoint de domínio. Containers pesados/stubs de IA (Ollama, Docling, GLiNER) e o container OpenSearch entram nos épicos que os usam, mantendo o build padrão rápido (regra de custo do SDD §1).

## Risks / Trade-offs

- [Testcontainers 2.x mudou API de forma incompatível] → migrar seguindo a documentação oficial 2.x (containers non-generic), validando com `spring-boot:test-run`; não copiar exemplos 1.x.
- [Keycloak `--import-realm` com override pode apagar ajustes manuais feitos via console] → tratado como feature: realm é infra-como-código; ajuste manual só vira permanente se commitado no JSON.
- [Sem endpoints de domínio no Épico 0, o `401` poderia parecer não-testável] → é testável: o Spring Security rejeita no filter chain, antes do roteamento — os cenários `@RF35` de token (sem token / expirado) fecham neste épico via harness E2E (decisão 7).
- [Toda a suíte BDD passa a depender de token por cenário] → cache de token por usuário no helper das steps (decisão 7; risco R13 do SDD `qualidade-e-testes.md` §3).

## Migration Plan

Ordem de execução pensada para cada passo destravar o seguinte: [0.1] wrapper → [0.8] pom → [0.3] portas → [0.5] env files/storage → [0.4] Flyway V1 → [0.7] Keycloak (infra → realm → resource server) → [0.2] Testcontainers (valida tudo via dev-mode). Rollback: git revert — nenhum ambiente tem estado persistente que precise ser preservado.

## Open Questions

- Versão exata do Keycloak a pinar no Dockerfile (ADL-008 diz "última versão") — resolver na task correspondente consultando a última estável.
