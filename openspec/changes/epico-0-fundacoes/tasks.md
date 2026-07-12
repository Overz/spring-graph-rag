# Tasks — Épico 0: Fundação e Débitos de Ambiente

> Grupos espelham as tarefas [0.x] do `docs/rag-plan.md`, na ordem do Migration Plan do `design.md`. DoD de cada grupo vem do plano.

## 1. Wrapper Maven — [0.1]

- [ ] 1.1 Regenerar o wrapper com `mvn -N wrapper:wrapper` e commitar `mvnw`/`mvnw.cmd`
- [ ] 1.2 Verificar DoD: `./mvnw clean compile` funciona numa máquina sem Maven global

## 2. Limpeza do pom — [0.8]

- [ ] 2.1 Remover `spring-ai-starter-model-chat-memory-repository-neo4j` do `pom.xml` (ADL-007)
- [ ] 2.2 Verificar DoD: `./mvnw clean compile` verde sem a dependência; módulo `chat` segue como placeholder

## 3. Portas — [0.3]

- [ ] 3.1 Mudar o default de `APP_PORT` para `8090` no `application.yaml`
- [ ] 3.2 Remapear o Adminer para `8081:8080` no `compose.yaml` (libera a 8080 para o Keycloak; Grafana permanece em 3000)
- [ ] 3.3 Atualizar comentários/docs que citem a porta 3000 da app (`compose.yaml`, `CLAUDE.md`)
- [ ] 3.4 Verificar DoD: `docker compose up` + `./mvnw spring-boot:run` sobem juntos sem conflito

## 4. Storage e limites — [0.5]

- [ ] 4.1 Criar `infra/minio/.env.minio` com o par único de credenciais e referenciá-lo (via `env_file`/variáveis) em `minio`, `juicefs-format` e `juicefs-mount`
- [ ] 4.2 Baixar `max-file-size`/`max-request-size` do multipart para `6MB` no `application.yaml` (RF03 — SDD `ingestao.md` §2)
- [ ] 4.3 Verificar DoD: `juicefs-format` executa com sucesso; mount em `./tmp/blobstore` gravável pela app

## 5. Flyway V1 core — [0.4]

> Fonte do schema: `docs/sdd/dados.md` §2 — detalha e **substitui** a §7.1 do rag-plan.

- [ ] 5.1 Substituir `V1__create_documents_table.sql` por `V1__baseline_documents.sql` com a tabela `documents` exatamente como no `dados.md` §2: `tenant_id`/`owner_id`, campos de arquivo NOT NULL (`extension`, `content_type`, `file_size_bytes`, `raw_storage_key`), `extracted_storage_key`/`transformed_storage_key`, `status` + sub-estados `embedding_status`/`graph_status`, `version`, `is_active`, `lgpd_redacted`, `correlation_id`, `uploaded_at`/`completed_at` em `TIMESTAMPTZ`, `UNIQUE (tenant_id, owner_id, file_hash_sha256, version)` e os índices `idx_documents_tenant_status` + `idx_documents_queued`
- [ ] 5.2 Incluir `document_status_history` (RF09 — com a coluna `branch` do fork-join) e `processing_errors` (RF28 — com a coluna `transient`), conforme `dados.md` §2
- [ ] 5.3 Verificar DoD: Flyway aplica num banco limpo; colunas cobrem RF06, RF08 e RF09. Demais tabelas do `dados.md` nascem com seus épicos: `tenant_quotas` (Ép. 1), `chunks` (Ép. 5), `entity_review_queue` (Ép. 6), `dead_letter_events` (Ép. 8), `audit_log` (Ép. 9)

## 6. Keycloak — infra ([0.7])

- [ ] 6.1 Criar `infra/postgres/.env.postgres` + `configure.sh` (padrão `POSTGRES_MULTIPLE_DATABASE_N=db/schema/user/pass`, entrada `keycloak/keycloak/keycloak/keycloak`) e montar como init script do Postgres no `compose.yaml`
- [ ] 6.2 Criar `infra/keycloak/` com Dockerfile multi-stage (`kc.sh build --db=postgres --health-enabled --metrics-enabled`, versão estável mais recente pinada — resolve a Open Question do design) e `.env.keycloak`
- [ ] 6.3 Escrever o realm `graphrag` versionado em `infra/keycloak/data/import/graphrag-realm.json`: protocol mapper da claim `tenantId`, clients `graphrag-api` e `graphrag-mcp-agent`, roles granulares (`document:*`, `query:read`, `admin:*`), seed de 2 tenants × 2 usuários + 1 admin + 1 client de agente por tenant (SDD `seguranca.md` §1.2)
- [ ] 6.4 Adicionar o serviço `keycloak` ao `compose.yaml` (porta default 8080, `--import-realm`, healthcheck em `/health/ready`, depends_on postgres)
- [ ] 6.5 Verificar: compose sobe, realm importado, token obtido via client `graphrag-api` carrega claim `tenantId`

## 7. Keycloak — resource server ([0.7], spec `autenticacao`)

- [ ] 7.1 Adicionar Spring Security OAuth2 Resource Server ao `pom.xml` e configurar o JWKS do realm no `application.yaml`
- [ ] 7.2 Criar `CallerContext {tenantId, ownerId, roles}` no módulo `shared` e o conversor único de claims (rejeição `401` para token sem claim `tenantId`)
- [ ] 7.3 Verificar DoD (spec `autenticacao`): requisição sem token → `401`; token expirado → `401`; token sem `tenantId` → `401`; nenhuma rota aberta além das explicitamente liberadas

## 8. Testcontainers 2.x — [0.2]

- [ ] 8.1 Migrar `TestcontainersConfiguration` para a API 2.x, reativando os beans de Postgres e Neo4j comentados
- [ ] 8.2 Verificar DoD: `./mvnw spring-boot:test-run` sobe a app com Postgres/Neo4j reais, sem Docker Compose

## 9. Fechamento do change

- [ ] 9.1 `./mvnw test` verde (`ModularityTest` + BDD com zero cenários ativos) e `graphify update .` executado
- [ ] 9.2 Atualizar `docs/rag-plan.md` §5 (estado atual) e `CLAUDE.md` (débitos zerados), conforme a regra de coerência do plano
