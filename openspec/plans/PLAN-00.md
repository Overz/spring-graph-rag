# PLAN-00 — Fundação e Débitos de Ambiente

> Deixar o repositório sem fricção antes do primeiro RF: build reprodutível, dev-mode funcionando, schema alinhado aos requisitos.

**[0.1] Regenerar o wrapper Maven** `[P · Must]`
`mvn -N wrapper:wrapper` e commitar `mvnw`/`mvnw.cmd`.
✅ **DoD:** `./mvnw clean compile` funciona numa máquina sem Maven global.

**[0.2] Migrar `TestcontainersConfiguration` para a API do Testcontainers 2.x** `[M · Must]`
Reativar os beans de Postgres e Neo4j (hoje comentados) com o padrão novo (containers non-generic).
✅ **DoD:** `./mvnw spring-boot:test-run` sobe a app com Postgres/Neo4j reais, sem Docker Compose.

**[0.3] Resolver o conflito de porta 3000 (app × Grafana)** `[P · Must]`
Escolher: mudar o default de `APP_PORT` ou remapear a porta publicada do `grafana-lgtm`.
✅ **DoD:** `docker compose up` + `./mvnw spring-boot:run` sobem juntos sem conflito.

**[0.4] Reescrever a migração Flyway com o schema dos requisitos** `[M · Must]`
Substituir a `V1` atual (modelo do plano antigo) pelo schema da seção 7: `tenant_id`/`owner_id`, ciclo de vida do RF08 com sub-estados, histórico, auditoria, erros estruturados. Como nenhum ambiente tem dado real, é reescrita da `V1`, não migração incremental.
✅ **DoD:** Flyway aplica o schema num banco limpo; colunas cobrem RF06, RF08 e RF09.

**[0.5] Corrigir as fricções do `compose.yaml`/`application.yaml`** `[P · Must]`
Credenciais do JuiceFS×MinIO alinhadas (fricção 5 da seção 5); `max-file-size` coerente com o RF03.
✅ **DoD:** `juicefs-format` executa com sucesso; mount em `./tmp/blobstore` gravável pela app.

**[0.6] Escrever o SDD com C4 até C3** `[G · Must]` — ✅ **concluído (julho/2026)**
Entregue como `sdd.md` (índice + Architecture Decision Log ADL-001..010) + `docs/sdd/` por domínio: C4 até C3, contratos, modelos de dados e as decisões fechadas em sessão de descoberta (NER via GLiNER, identidade via Keycloak, consulta stateless síncrona, formato físico do SDD).

**[0.7] Keycloak no `compose.yaml` + app como resource server** `[M · Must]`
Padrão da ADL-008: build otimizado (`kc.sh build --db=postgres --health-enabled --metrics-enabled`), `.env.keycloak`, realm `graphrag` versionado em JSON (2 tenants × 2 usuários de teste + roles granulares por operação) importado no start; app com Spring Security OAuth2 Resource Server e `CallerContext` resolvido das claims. Detalhe em `sdd/seguranca.md` §1.
✅ **DoD:** upload/status só funcionam com JWT válido do realm; token sem claim `tenantId` é rejeitado com `401`.

**[0.8] Remover a dependência de chat memory do `pom.xml`** `[P · Must]`
`spring-ai-starter-model-chat-memory-repository-neo4j` sai (ADL-007 — consulta stateless; multi-turno é ponto de extensão sem RF).
✅ **DoD:** build verde sem a dependência; módulo `chat` segue como placeholder.
