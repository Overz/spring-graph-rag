Mapeia RF35 / `docs/rag-plan.md` [9.4] ("Endurecer AuthN/AuthZ"). Pontos do design.md marcados **[decisão pendente]** devem ser confirmados com o usuário antes de iniciar a implementação — ver `design.md` §Open Questions.

## 1. Decisões de arquitetura a confirmar antes de codar

- [x] 1.1 ~~Confirmar D1~~ — resolvido: pacote `api/internal/auth/`, sem módulo Spring Modulith novo
- [ ] 1.2 Confirmar path/nome definitivo do(s) endpoint(s) (`/api/v1/auth/login`, `/api/v1/auth/logout`?)
- [ ] 1.3 Decidir se abre ADR novo (`docs/adr/ADR-004-...`) para D2 (resolução dual de token) ou se fica só em design.md
- [ ] 1.4 Confirmar se logout entra neste change ou fica para uma tarefa separada

## 2. Infraestrutura e dependências

- [ ] 2.1 Adicionar `spring-boot-starter-data-redis` no `pom.xml`
- [ ] 2.2 Configurar `app.auth.redis.database` (ou equivalente) em `application.yaml` — database lógico diferente do `1` usado pelo JuiceFS (ver design.md D3)
- [ ] 2.3 `TestcontainersConfiguration` ganha um container Redis (hoje só Postgres/Neo4j/Keycloak)

## 3. Pacote `api/internal/auth/`

- [ ] 3.1 Criar o pacote `api/internal/auth/` (exceção pontual ao layout por camada, ver design.md D1 — sem `package-info.java`/`@ApplicationModule` novo, continua dentro do módulo `api` existente)
- [ ] 3.2 Interface `PhantomTokenIssuer` (contrato: emitir a partir de usuário/senha, revogar a partir do token opaco)
- [ ] 3.3 Implementação do password grant fixo (realm `graphrag`, client `graphrag-api`, `grant_type=password`) contra o Keycloak, reaproveitando o padrão de `KeycloakTokens.java` (agora em código de produção)
- [ ] 3.4 Repositório Redis do phantom token (chave `phantom-token:{token}`, valor = claims, TTL = `expires_in` do Keycloak)
- [ ] 3.5 Gerador de token opaco de alta entropia (`SecureRandom`, ver design.md D4)
- [ ] 3.6 Erros de domínio (sufixo `Exception`, mesmo padrão de `api/internal/errors/`): credenciais inválidas, token revogado/inexistente

## 4. Integração com o resource server (`SecurityConfig`)

- [ ] 4.1 `AuthenticationManagerResolver`/filtro que distingue JWT (2 pontos) de token opaco (design.md D2)
- [ ] 4.2 `OpaqueTokenIntrospector` que consulta `PhantomTokenIssuer`/Redis e monta o mesmo formato de claims que `CallerContextJwtConverter` já produz para JWT
- [ ] 4.3 Confirmar que `CallerContext` final é idêntico nos dois caminhos (JWT direto e token opaco) — nenhuma mudança em controllers/serviços que já consomem `CallerContext`
- [ ] 4.4 Controller(s) HTTP: `POST /api/v1/auth/login`, `POST /api/v1/auth/logout` (se 1.4 confirmar escopo), dependendo só de `PhantomTokenIssuer`

## 5. Testes

- [ ] 5.1 Testes de unidade da lógica de negócio nova (geração/validação de token opaco, resolução de claims via stub de Redis) — seguir convenção do projeto: `extends Assertions`, um assert por teste, stub à mão em vez de Mockito
- [ ] 5.2 Adicionar cenários `@RF35` em `seguranca/autenticacao-e-criptografia.feature` cobrindo os requirements novos de `specs/auth-phantom-token/spec.md` e `specs/autenticacao/spec.md` (login válido/inválido, logout, token opaco revogado)
- [ ] 5.3 `./mvnw test` verde (BDD sem `@pendente` nos novos cenários + `ModularityTest`)

## 6. Documentação

- [ ] 6.1 `docs/http/auth/01-login.http` e, se aplicável, `docs/http/auth/02-logout.http` (convenção de `docs/http/`)
- [ ] 6.2 Atualizar `docs/sdd/seguranca.md` §1 com o fluxo de phantom token (apontar para este change, não duplicar)
- [ ] 6.3 Atualizar `CLAUDE.md` — seção "Package layout", citar `auth/` como segunda exceção pontual (ao lado de `shared`)
- [ ] 6.4 Atualizar `docs/rag-plan.md` [9.4] referenciando este change
- [ ] 6.5 Se 1.3 confirmar, criar `docs/adr/ADR-004-...`
- [ ] 6.6 `openspec/specs/autenticacao/spec.md` e novo `openspec/specs/auth-phantom-token/spec.md` sincronizados no archive ao final (`openspec archive`)
