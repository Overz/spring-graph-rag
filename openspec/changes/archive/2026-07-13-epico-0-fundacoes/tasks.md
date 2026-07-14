# Tasks — Épico 0: Fundação e Débitos de Ambiente

> Grupos espelham as tarefas [0.x] do `docs/rag-plan.md`, na ordem do Migration Plan do `design.md`. DoD de cada grupo vem do plano.

## 1. Wrapper Maven — [0.1]

- [x] 1.1 Regenerar o wrapper com `mvn -N wrapper:wrapper` e commitar `mvnw`/`mvnw.cmd` (feito pelo usuário)
- [x] 1.2 Verificar DoD: `./mvnw clean compile` funciona numa máquina sem Maven global

## 2. Limpeza do pom — [0.8]

- [x] 2.1 Remover `spring-ai-starter-model-chat-memory-repository-neo4j` do `pom.xml` (ADL-007)
- [x] 2.2 Verificar DoD: `./mvnw clean compile` verde sem a dependência; módulo `chat` segue como placeholder

## 3. Portas — [0.3]

- [x] 3.1 Mudar o default de `APP_PORT` para `8090` no `application.yaml`
- [x] 3.2 Remapear o Adminer para `8081:8080` no `compose.yaml` (libera a 8080 para o Keycloak; Grafana permanece em 3000)
- [x] 3.3 Atualizar comentários/docs que citem a porta 3000 da app (`compose.yaml`, `CLAUDE.md`)
- [x] 3.4 Verificar DoD: portas disjuntas por construção (app 8090; compose com Keycloak 8080/Adminer 8081/Grafana 3000, todos publicados e testados); app validada subindo junto ao stack via suíte E2E

## 4. Storage e limites — [0.5]

- [x] 4.1 Criar `infra/minio/.env.minio` com o par único de credenciais e referenciá-lo (via `env_file`/variáveis) em `minio`, `juicefs-format` e `juicefs-mount`
- [x] 4.2 Baixar `max-file-size`/`max-request-size` do multipart para `6MB` no `application.yaml` (RF03 — SDD `ingestao.md` §2)
- [x] 4.3 Verificar DoD: `juicefs-format` executa com sucesso; mount em `./tmp/blobstore` gravável pela app (fix extra descoberto: AppArmor ativo neste host exigia `security_opt: apparmor:unconfined` no `juicefs-mount` — documentado no próprio `compose.yaml`)

## 5. Flyway V1 core — [0.4]

> Fonte do schema: `docs/sdd/dados.md` §2 — detalha e **substitui** a §7.1 do rag-plan.

- [x] 5.1 Substituir `V1__create_documents_table.sql` por `V1__baseline_documents.sql` com a tabela `documents` exatamente como no `dados.md` §2: `tenant_id`/`owner_id`, campos de arquivo NOT NULL (`extension`, `content_type`, `file_size_bytes`, `raw_storage_key`), `extracted_storage_key`/`transformed_storage_key`, `status` + sub-estados `embedding_status`/`graph_status`, `version`, `is_active`, `lgpd_redacted`, `correlation_id`, `uploaded_at`/`completed_at` em `TIMESTAMPTZ`, `UNIQUE (tenant_id, owner_id, file_hash_sha256, version)` e os índices `idx_documents_tenant_status` + `idx_documents_queued`
- [x] 5.2 Incluir `document_status_history` (RF09 — com a coluna `branch` do fork-join) e `processing_errors` (RF28 — com a coluna `transient`), conforme `dados.md` §2
- [x] 5.3 Verificar DoD: SQL aplicado com sucesso num banco limpo (`v1_check` via psql); a execução via Flyway acontece no boot da app em 8.3. Demais tabelas do `dados.md` nascem com seus épicos: `tenant_quotas` (Ép. 1), `chunks` (Ép. 5), `entity_review_queue` (Ép. 6), `dead_letter_events` (Ép. 8), `audit_log` (Ép. 9)

## 6. Keycloak — infra ([0.7])

- [x] 6.1 Criar `infra/postgres/.env.postgres` + `configure.sh` (padrão `POSTGRES_MULTIPLE_DATABASE_N=db/schema/user/pass`, entrada `keycloak/keycloak/keycloak/keycloak`) e montar como init script do Postgres no `compose.yaml` (nota: postgres:18+ mudou o datadir — mount corrigido para `/var/lib/postgresql`)
- [x] 6.2 Criar `infra/keycloak/` com Dockerfile multi-stage (`kc.sh build --db=postgres --health-enabled --metrics-enabled`, **26.7.0** pinada — Open Question do design resolvida) e `.env.keycloak`
- [x] 6.3 Escrever o realm `graphrag` versionado em `infra/keycloak/data/import/graphrag-realm.json`: protocol mapper da claim `tenantId` (scope `tenant` default), client `graphrag-api` + `graphrag-mcp-agent-{acme,globex}`, roles granulares, seed 2 tenants × (2 usuários + 1 admin) + service accounts com `tenantId`; extra: client `graphrag-e2e` (lifespan 1s) para o cenário de token expirado
- [x] 6.4 Adicionar o serviço `keycloak` ao `compose.yaml` (porta default 8080, `--import-realm`, healthcheck em `/health/ready`, depends_on postgres)
- [x] 6.5 Verificar: compose sobe, realm importado, token obtido via client `graphrag-api` carrega claim `tenantId` (verificado: `tenantId=acme_inc`, roles granulares presentes)

## 7. Keycloak — resource server ([0.7], spec `autenticacao`)

- [x] 7.1 Adicionar Spring Security OAuth2 Resource Server ao `pom.xml` e configurar o JWKS do realm no `application.yaml` (+ decoder com clock skew zero para "expirado" ser exato)
- [x] 7.2 Criar `CallerContext {tenantId, ownerId, roles}` no módulo `shared` e o conversor único de claims (rejeição `401` para token sem claim `tenantId`) + argument resolver
- [x] 7.3 Verificar DoD (spec `autenticacao`): cenários E2E `@RF35` verdes (sem token → `401`, expirado → `401`); rotas liberadas: só `/actuator/health/**` e `/actuator/prometheus`

## 8. Testcontainers 2.x — [0.2]

- [x] 8.1 Migrar `TestcontainersConfiguration` para a API 2.x, reativando os beans de Postgres e Neo4j comentados (versões pinadas iguais às do compose)
- [x] 8.2 Adicionar o container **Keycloak** à `TestcontainersConfiguration`, importando o **mesmo** `infra/keycloak/data/import/graphrag-realm.json` do compose (SDD `qualidade-e-testes.md` §3 — um realm só, dev e teste idênticos) + `DynamicPropertyRegistrar` do issuer
- [x] 8.3 Verificar DoD: `./mvnw spring-boot:test-run` sobe a app com Postgres/Neo4j/Keycloak reais, sem Docker Compose (`Started Application`, Tomcat em 8090)

## 9. Harness E2E/BDD — pavimentação de qualidade (SDD `qualidade-e-testes.md` §§2–3)

- [x] 9.1 Criar a classe `@CucumberContextConfiguration` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` no pacote `com.github.overz.bdd`, importando a `TestcontainersConfiguration` (`cucumber-spring` já está no classpath)
- [x] 9.2 Criar o helper de tokens das steps (`KeycloakTokens`): JWT reais via password grant dos usuários seedados, cache por usuário (R13) + client `graphrag-e2e` de lifespan 1s para token expirado real
- [x] 9.3 Automatizar as steps dos cenários `@RF35` de token e remover `@pendente` deles — primeiros cenários E2E verdes do projeto (`./mvnw test`: 2 executados, 0 falhas; desbloqueou 3 débitos latentes de boot: sabor AWS do OpenSearch, `uris` obrigatório, registry duplicado do Modulith)

## 10. Fechamento do change

- [x] 10.1 `./mvnw test` verde (`ModularityTest` 3/3 + 2 cenários `@RF35` executados, 148 `@pendente` skipped) e `graphify update .` executado
- [x] 10.2 Atualizar `CLAUDE.md` (débitos zerados, harness E2E existente) e o estado atual em `docs/rag-plan.md` §5
