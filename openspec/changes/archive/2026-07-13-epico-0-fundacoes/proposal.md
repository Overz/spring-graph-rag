# Épico 0 — Fundação e Débitos de Ambiente

## Why

O repositório carrega débitos de ambiente que fazem o Épico 1 (RF01–RF07) nascer sobre fundações erradas: a migração Flyway segue o plano antigo (sem `tenant_id`/`owner_id`, ciclo de vida pré-RF08), o dev-mode não sobe (Testcontainers 2.x), app e Grafana disputam a porta 3000, o `juicefs-format` falha por credenciais divergentes e a identidade — que pela ADL-008 é real desde o dia 1 — ainda não existe. Este change cobre o **Épico 0 do `docs/rag-plan.md`** (tarefas [0.1]–[0.5], [0.7], [0.8]; a [0.6]/SDD já está concluída) e é pré-requisito de todos os épicos seguintes.

## What Changes

- **[0.1]** Wrapper Maven regenerado (`mvn -N wrapper:wrapper`) — `./mvnw` funciona sem Maven global.
- **[0.2]** `TestcontainersConfiguration` migrada para a API do Testcontainers 2.x — beans de Postgres/Neo4j reativados; `./mvnw spring-boot:test-run` volta a funcionar.
- **[0.3]** App sai da porta 3000: default de `APP_PORT` passa a **8090**; serviços do `compose.yaml` ficam nas portas default (Grafana 3000, Keycloak 8080; Adminer remapeado para 8081).
- **[0.4]** **BREAKING** (schema, sem dado real em nenhum ambiente): `V1__create_documents_table.sql` substituída pela V1 core com o schema de `docs/sdd/dados.md` §2 — `documents` (com `tenant_id`, `owner_id`, sub-estados de fork-join, `is_active`, `correlation_id`, unicidade RF07) + `document_status_history` (RF09) + `processing_errors` (RF28). As demais tabelas do `dados.md` nascem na migração do épico que as usa (`tenant_quotas` → Ép. 1, `chunks` → Ép. 5, `entity_review_queue` → Ép. 6, `dead_letter_events` → Ép. 8, `audit_log` → Ép. 9).
- **[0.5]** Credenciais MinIO×JuiceFS unificadas em `infra/minio/.env.minio` (compartilhado no compose); `max-file-size` do multipart baixa de 20MB para **6MB** — pouco acima do limite de negócio de 5MB (RF03), para a validação de domínio responder antes do container.
- **[0.7]** Keycloak no `compose.yaml` (ADL-008): database dedicado na instância Postgres existente via `infra/postgres/.env.postgres` + `configure.sh` (padrão `POSTGRES_MULTIPLE_DATABASE_N=db/schema/user/pass`), realm `graphrag` versionado em JSON e importado no start; app vira OAuth2 Resource Server com `CallerContext` resolvido das claims.
- **[0.8]** Dependência `spring-ai-starter-model-chat-memory-repository-neo4j` removida do `pom.xml` (ADL-007 — consulta stateless).
- **Harness E2E/BDD** (pavimentação de qualidade, SDD `qualidade-e-testes.md` §§2–3): classe `@CucumberContextConfiguration` + `@SpringBootTest(RANDOM_PORT)` importando a `TestcontainersConfiguration` (que ganha container Keycloak com o mesmo realm JSON do compose), helper de tokens com cache por usuário — e os cenários `@RF35` de token saem de `@pendente`, tornando-se os primeiros E2E verdes do projeto.

## Capabilities

### New Capabilities

- `autenticacao`: toda a API exige JWT válido do realm `graphrag`; token sem claim `tenantId` é rejeitado; `tenantId`/`ownerId` saem **sempre** do `CallerContext` (claims), nunca do corpo da requisição. Cobre a fatia do RF35 exigida pelo DoD do [0.7] — os cenários restantes de `seguranca/autenticacao-e-criptografia.feature` (AuthN de MCP, criptografia) fecham no Épico 9 ([9.4]/[9.5]).

### Modified Capabilities

Nenhuma — `openspec/specs/` está vazio (primeiro change do projeto).

## Impact

- **Build/dev:** `mvnw`/`mvnw.cmd` novos; `pom.xml` (dependência removida); `TestcontainersConfiguration` reescrita.
- **Infra:** `compose.yaml` (Keycloak novo, porta do Adminer, env files); `infra/postgres/` (`.env.postgres`, `configure.sh`), `infra/minio/.env.minio`, `infra/keycloak/` (Dockerfile, `.env.keycloak`, realm JSON) novos.
- **App:** `application.yaml` (porta 8090, `max-file-size: 6MB`, config de resource server); Spring Security OAuth2 Resource Server + `CallerContext` no código (módulos `shared`/`api`).
- **Banco:** `src/main/resources/db/migration/V1__*` reescrita (nome novo coerente com o conteúdo).
- **BDD:** os dois cenários `@RF35` de token (sem token / expirado) **fecham** neste épico (o `401` vem do filter chain, não depende de endpoint de domínio); o harness Cucumber+Spring+Testcontainers e o realm de teste (2 tenants × 2 usuários + admin + client de agente por tenant) passam a existir para toda a suíte.
