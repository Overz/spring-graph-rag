## Context

Design canônico da autenticação: `docs/sdd/seguranca.md` §1 (ADL-008) — não repetido aqui. Estado atual relevante para este change (verificado no repositório, não só no SDD):

- `SecurityConfig` (`api/internal/configs`) hoje só valida JWT real via JWKS (`NimbusJwtDecoder`, clock skew zero, `SupplierJwtDecoder` para resolução preguiçosa do issuer). Um único `JwtDecoder` bean, um único `oauth2ResourceServer().jwt(...)`.
- `CallerContextJwtConverter` (`api/internal/security`) materializa `CallerContext{tenantId, ownerId, roles}` a partir de um `Jwt` já validado pelo Spring Security.
- `KeycloakTokens.java` (código de teste) já faz exatamente o password grant que este change precisa fazer em produção: `POST {issuer}/protocol/openid-connect/token`, form `grant_type=password&client_id=graphrag-api&username=...&password=...`.
- Redis já existe em `compose.yaml` (`redis:8.8.0`), mas dedicado à metadata do JuiceFS (`redis://redis:6379/1`). Nenhuma dependência Spring Data Redis no `pom.xml`.
- MCP (client_credentials, service accounts `graphrag-mcp-agent-*`) não passa por login de usuário — continua recebendo JWT direto do Keycloak. Este change não altera esse caminho.

## Goals / Non-Goals

**Goals:**
- Emitir um token opaco ("phantom token") para o usuário final autenticar via `POST /api/v1/auth/login` (realm e `grant_type` fixos), sem expor claims do JWT real ao cliente.
- Guardar o JWT/claims real no Redis, chaveado pelo token opaco, com TTL = expiração do token do Keycloak.
- Permitir revogação imediata (`POST /api/v1/auth/logout` apaga a chave no Redis — o JWT real pode continuar tecnicamente válido até o `exp`, mas deixa de ser aceito por esta API porque o phantom token que o protege já não resolve para nada).
- Resource server aceitar tanto o token opaco (rotas de usuário) quanto o JWT direto (rotas usadas por service account/MCP), sem regressão nos cenários `@RF35` já verdes.
- Endpoint(s) expostos por interface (contrato explícito, implementação trocável).

**Non-Goals (deste change):**
- Migrar MCP/client_credentials para phantom token — fora de escopo, service accounts continuam com JWT direto.
- Refresh token / renovação silenciosa de sessão — login expira, usuário loga de novo. Pode virar change futuro.
- Multi-sessão por usuário com listagem/revogação seletiva — v1 é uma chave por token emitido, sem índice por usuário.
- Rate limiting/brute-force protection no login — nota de risco abaixo, mas implementação fica para o Épico 9 mais amplo ou change dedicado.

## Decisions

### D1 — Módulo novo `auth` (não pacote dentro de `api`)

Autenticação é bounded context próprio, distinto de "ingestão de dados" (propósito documentado de `api`). Vira módulo Spring Modulith top-level, sibling de `api`/`rag`/`chat`/`mcp`/`shared`, com API pública análoga a `DocumentCommandApi` do `rag` — ex. uma interface `PhantomTokenIssuer` (emitir/revogar) que `api` consome via `allowedDependencies += "auth"`, e que o `SecurityConfig` do `api` usa para resolver o token opaco recebido no `Authorization` header.

*Alternativa considerada:* pacote `api/internal/auth/`. Mais rápido, mas mistura identidade dentro do módulo de ingestão e não prepara terreno para o MCP eventualmente reusar o mesmo mecanismo. Rejeitada — o próprio RF35 já trata AuthN como preocupação cross-cutting (vale igual para REST e MCP), então o módulo `auth` deve poder ser consumido por `mcp` no futuro sem reorganizar nada.

*Consequência:* `Application.java`/`ModularityTest` passam a reconhecer o módulo `auth`; `api/package-info.java` ganha `"auth"` em `allowedDependencies` (mais `shared::*` que `auth` também precisar). Atualizar CLAUDE.md (Architecture, Module Dependency Rules).

### D2 — Resolução dual de token no resource server (opaco vs. JWT)

Um `AuthenticationManagerResolver<HttpServletRequest>` (ou filtro equivalente) decide por formato: JWT tem 2 pontos (`header.payload.signature`); qualquer outra coisa é tratado como token opaco. JWT segue o caminho já existente (`NimbusJwtDecoder` + `CallerContextJwtConverter`, inalterado). Token opaco vira uma implementação de `OpaqueTokenIntrospector` que consulta o Redis via o módulo `auth`, recupera as claims cacheadas e monta o mesmo formato de principal que o `CallerContextJwtConverter` produz — ambos os caminhos convergem no mesmo `CallerContext`.

*Alternativa considerada:* `oauth2ResourceServer().opaqueToken(...)` sozinho, chamando o Keycloak em toda requisição para introspecção real (RFC 7662). Rejeitada — reintroduz round-trip ao Keycloak por requisição, exatamente o custo que o Redis existe para evitar; o Redis já guarda o resultado da validação feita uma vez no login.

### D3 — Schema Redis

Chave: `phantom-token:{tokenOpaco}` (prefixo evita colisão de namespace se o mesmo Redis for reusado por outra feature depois). Valor: as claims já extraídas (`tenantId`, `ownerId`/`sub`, `roles`) serializadas — não o JWT bruto. Guardar só as claims (não o JWT completo) evita reimplementar validação de assinatura a cada requisição; a validação de assinatura/issuer já aconteceu uma vez, no momento do login, contra o Keycloak real. TTL da chave = `expires_in` do token retornado pelo Keycloak no login (mesmo valor que `KeycloakTokens.java` já usa para cache de teste).

*Database lógico:* Redis existente (`redis:6379`) já serve o JuiceFS no db `1`. Este change usa um db lógico diferente (`2`) na mesma instância — evita provisionar um segundo Redis num projeto que já roda 100% local; databases lógicos do Redis isolam os keyspaces sem overhead de infra novo.

### D4 — Token opaco = identificador aleatório de alta entropia

Gerado com `SecureRandom` (ex. 32 bytes, codificado base64url) — não é um JWT, não é previsível, não carrega nenhuma informação decodificável pelo cliente. Isso é o próprio ponto do padrão: o token não significa nada fora do Redis desta API.

### D5 — Contrato via interface

`PhantomTokenIssuer` (nome sugerido, módulo `auth`) com métodos equivalentes a `issue(username, password) -> PhantomToken` e `revoke(token) -> void`. O controller HTTP do módulo `auth` depende só dessa interface — trocar a implementação (ex. outra estratégia de token store, ou um v2 do fluxo) não toca o controller nem quem consome via `allowedDependencies`.

### D6 — ROPC é aceito conscientemente

O login endpoint recebe usuário+senha do usuário final e repassa como `grant_type=password` ao Keycloak — é o fluxo Resource Owner Password Credentials, considerado anti-padrão para APIs públicas modernas (cliente teria que confiar credenciais a um backend em vez de usar redirect/PKCE). Aceito aqui porque (a) já é exatamente o que `KeycloakTokens.java` faz em teste, (b) o projeto é 100% local/aprendizado, sem exposição pública, (c) o objetivo do change é o padrão Phantom Token em si, não uma reforma do grant type. Se o projeto algum dia expuser a API publicamente, isso precisa ser revisitado (authorization code + PKCE).

## Risks / Trade-offs

- **[Risco] Redis indisponível derruba toda autenticação de usuário** (login e toda requisição autenticada dependem do lookup) → Mitigação: Redis já é dependência dura do JuiceFS hoje (sem ele o storage nem monta) — não é uma dependência nova no grafo de disponibilidade do projeto, só um uso a mais da mesma instância.
- **[Risco] Sessão "zumbi": usuário deslogado (Redis limpo) mas o JWT real ainda válido no Keycloak** → Mitigação: por design, o JWT real nunca é aceito diretamente nas rotas de usuário depois deste change (só o token opaco é aceito nesse caminho) — revogar no Redis já basta.
- **[Risco] `grant_type=password` sem rate limiting no login vira alvo de força bruta** → Mitigação: fora de escopo deste change (ver Non-Goals); registrar como débito para o Épico 9 mais amplo.
- **[Trade-off] Guardar só claims (não o JWT bruto) no Redis** significa que, se o formato de claims mudar no realm, tokens já emitidos ficam com o formato antigo até expirar (TTL curto, mesmo problema de qualquer cache — aceitável).

## Migration Plan

Aditivo, sem remover nada: rotas continuam aceitando JWT direto (MCP/service accounts inalterado); usuário final passa a ter a opção de logar via `/api/v1/auth/login` em vez de falar com o Keycloak diretamente — nenhum cliente existente quebra, o `.http` de Keycloak (`docs/http/keycloak/01-obter-token.http`) continua funcionando para debug/dev. Rollback = remover o novo módulo e as rotas; resource server volta a só aceitar JWT (comportamento anterior inalterado no código).

## Open Questions

1. Nome definitivo do(s) endpoint(s) e do path (`/api/v1/auth/login` + `/logout` — confirmar convenção de path com o usuário, hoje só existe `/api/v1/documents`).
2. Confirmar D1 (módulo novo `auth`) com o usuário antes de mexer em `Application.java`/`ModularityTest`/`package-info.java` — é a decisão de maior impacto estrutural deste change.
3. Vale um ADR novo (`docs/adr/ADR-004-...`) para D1+D2, dado o peso arquitetural, ou fica só registrado aqui em design.md? (ADL-008 em `sdd.md` já é "descoberta, ADR pendente" — pode ser o mesmo ADR endereçando os dois).
4. Logout entra neste change ou vira uma tarefa separada — usuário mencionou "podemos ter mais de um endpoint" sem fechar quais.
