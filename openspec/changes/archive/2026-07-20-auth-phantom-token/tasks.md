Mapeia RF35 / `docs/rag-plan.md` [9.4] ("Endurecer AuthN/AuthZ"). Todas as decisões de arquitetura foram confirmadas em 2026-07-20 — ver `design.md` §Open Questions (todas resolvidas). Implementado e testado em 2026-07-20 (159/159 testes verdes, `ModularityTest` incluso).

## 1. Decisões de arquitetura (todas resolvidas)

- [x] 1.1 D1 — pacote `api/internal/auth/`, sem módulo Spring Modulith novo
- [x] 1.2 D8 — endpoints por ação: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`
- [x] 1.3 Abre ADR novo — `docs/adr/ADR-004-phantom-token-login.md`
- [x] 1.4 Logout e refresh entram neste change (D7) — escopo final é login + refresh + logout

## 2. Infraestrutura e dependências

- [x] 2.1 Adicionar `spring-boot-starter-data-redis` no `pom.xml`
- [x] 2.2 Configurar `spring.data.redis.*` (host/port/database) em `application.yaml` — database lógico `2`, diferente do `1` usado pelo JuiceFS (ver design.md D3); `app.auth.keycloak-client-id` também externalizado
- [x] 2.3 `TestcontainersConfiguration` ganha um container Redis (`GenericContainer` + `@ServiceConnection(name="redis")` — não existe módulo Testcontainers não-genérico pra Redis, mesma situação do Keycloak)

## 3. Pacote `api/internal/auth/`

- [x] 3.1 Pacote `api/internal/auth/` criado (exceção pontual ao layout por camada, ver design.md D1 — sem `package-info.java`/`@ApplicationModule` novo)
- [x] 3.2 Interface `PhantomTokenIssuer` (`issue`, `refresh`, `revoke`, e `resolve` — adicionado durante a implementação para o resource server consultar o `CallerContext` sem expor o repositório Redis publicamente)
- [x] 3.3 `KeycloakTokenClient` — password grant e refresh_token grant fixos, reaproveitando o padrão de `KeycloakTokens.java`
- [x] 3.4 `PhantomTokenRepository` (Redis, hash por chave `phantom-token:{token}`, campos em vez de JSON serializado — evita qualquer dependência de Jackson clássico; TTL = `refresh_expires_in`)
- [x] 3.5 `OpaqueTokenGenerator` (`SecureRandom`, 32 bytes, base64url)
- [x] 3.6 `AuthRejectedException` + `InvalidCredentialsException`/`InvalidPhantomTokenException` em `api/internal/errors/` (mesmo padrão de `UploadRejectedException`)

## 4. Integração com o resource server (`SecurityConfig`)

- [x] 4.1 `AuthenticationManagerResolver<HttpServletRequest>` que distingue JWT (2 pontos) de token opaco (design.md D2)
- [x] 4.2 **Ajuste de design durante a implementação:** em vez de `OpaqueTokenIntrospector` (que força o formato `BearerTokenAuthentication` do Spring), foi escrito um `AuthenticationProvider` customizado (`PhantomTokenAuthenticationProvider`) + `PhantomTokenAuthenticationToken` — mesmo padrão de `CallerContextAuthenticationToken`/`CallerContextJwtConverter` já usado no caminho JWT, garantindo que `CallerContextArgumentResolver` funcione idêntico nos dois caminhos sem mudança nenhuma
- [x] 4.3 `CallerContext` final idêntico nos dois caminhos — extração de claims compartilhada via `CallerContextJwtConverter.toCallerContext(Jwt)` (extraído como método estático reaproveitado por `PhantomTokenIssuerImpl` ao decodificar o access token recém-emitido pelo Keycloak)
- [x] 4.4 `AuthController`: `POST /api/v1/auth/login|refresh|logout`, dependendo só de `PhantomTokenIssuer`; `/login` liberado (`permitAll`), os outros dois exigem credencial válida via o resolver acima

## 5. Testes

- [x] 5.1 `OpaqueTokenGeneratorTest`, `CallerContextJwtConverterTest` (nova extração `toCallerContext`), `PhantomTokenAuthenticationProviderTest` (stub de `PhantomTokenIssuer`) — `PhantomTokenIssuerImpl` em si não ganhou teste de unidade isolado (depende de classes concretas — `KeycloakTokenClient`/`PhantomTokenRepository` — não interfaces; cobertura real vem da suíte BDD abaixo, contra Keycloak+Redis reais via Testcontainers, consistente com a convenção do projeto de não usar Mockito)
- [x] 5.2 5 cenários `@RF35` novos em `seguranca/autenticacao-e-criptografia.feature`: login válido (token não é JWT), login inválido (401), requisição autenticada com token opaco aceita, logout revoga (401 depois), refresh mantém o mesmo token opaco
- [x] 5.3 `./mvnw test` verde: 159/159 (85 `@pendente` esperados, não relacionados), `ModularityTest` incluso

## 6. Documentação

- [x] 6.1 `docs/http/auth/01-login.http`, `02-refresh.http`, `03-logout.http`
- [x] 6.2 `docs/sdd/seguranca.md` §1 atualizado (implementado, não mais proposto)
- [x] 6.3 `CLAUDE.md` — "Package layout" cita `auth/` como segunda exceção pontual
- [x] 6.4 `docs/rag-plan.md` [9.4] atualizado (feito, não mais proposto)
- [x] 6.5 `docs/adr/ADR-004-phantom-token-login.md` criado (D1, D2, D7, D8)
- [x] 6.6 `openspec archive` — `openspec/specs/autenticacao/spec.md` sincronizado (delta MODIFIED aplicado) e `openspec/specs/auth-phantom-token/spec.md` criado
