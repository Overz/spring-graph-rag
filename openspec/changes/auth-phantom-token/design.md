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

### D1 — Pacote `auth` dentro do módulo `api` (não módulo novo) — revisado

Decisão inicial (v1 deste design.md) recomendava um módulo Spring Modulith novo, com o argumento de que `mcp` poderia reusar o mesmo mecanismo no futuro. Esse argumento não se sustenta: `mcp` autentica via service account **client_credentials** (`graphrag-mcp-agent-*`), nunca via `grant_type=password` — não existe cenário em que `mcp` chame um endpoint de login de usuário. Login é, na prática, só mais um HTTP entry point de usuário final, exatamente o que `api` já é descrito como fazendo (`CLAUDE.md`: *"HTTP entry: upload + validations, status/history, query REST, admin"*) — cabe em `api` sem forçar nada.

O contrato via interface que o usuário pediu ("permitir implementações futuras") também não exige fronteira de módulo — `DocumentStorage` (porta em `shared`) e `MalwareScanner` (porta em `api`) já são interfaces simples dentro do módulo, sem precisar virar módulo próprio, e cumprem exatamente esse papel hoje.

**Decisão:** pacote `api/internal/auth/` — tratado como uma exceção pontual ao layout por camada (`configs/controllers/services/.../errors`) documentado em `CLAUDE.md`, do mesmo jeito que `shared` já é uma exceção documentada (lá por usar `@NamedInterface`; aqui por ser um sub-domínio pequeno e coeso o suficiente pra não valer a pena espalhar 5–6 classes entre as pastas de camada existentes). Contém controller, o `PhantomTokenIssuer` (interface) + implementação, o repositório Redis e as exceções de domínio deste fluxo.

*Consequência:* nenhuma mudança em `Application.java`/`ModularityTest` além do que já existe para `api`; `api/package-info.java` não ganha dependência nova (tudo interno ao próprio módulo). `CLAUDE.md` ganha só uma nota curta em "Package layout" citando `auth/` como a segunda exceção pontual (ao lado de `shared`).

### D2 — Resolução dual de token no resource server (opaco vs. JWT)

Um `AuthenticationManagerResolver<HttpServletRequest>` (ou filtro equivalente) decide por formato: JWT tem 2 pontos (`header.payload.signature`); qualquer outra coisa é tratado como token opaco. JWT segue o caminho já existente (`NimbusJwtDecoder` + `CallerContextJwtConverter`, inalterado). Token opaco vira uma implementação de `OpaqueTokenIntrospector` que consulta o Redis via o pacote `auth`, recupera as claims cacheadas e monta o mesmo formato de principal que o `CallerContextJwtConverter` produz — ambos os caminhos convergem no mesmo `CallerContext`.

*Alternativa considerada:* `oauth2ResourceServer().opaqueToken(...)` sozinho, chamando o Keycloak em toda requisição para introspecção real (RFC 7662). Rejeitada — reintroduz round-trip ao Keycloak por requisição, exatamente o custo que o Redis existe para evitar; o Redis já guarda o resultado da validação feita uma vez no login.

### D3 — Schema Redis

Chave: `phantom-token:{tokenOpaco}` (prefixo evita colisão de namespace se o mesmo Redis for reusado por outra feature depois). Valor: as claims já extraídas (`tenantId`, `ownerId`/`sub`, `roles`) serializadas — não o JWT bruto. Guardar só as claims (não o JWT completo) evita reimplementar validação de assinatura a cada requisição; a validação de assinatura/issuer já aconteceu uma vez, no momento do login, contra o Keycloak real. TTL da chave = `expires_in` do token retornado pelo Keycloak no login (mesmo valor que `KeycloakTokens.java` já usa para cache de teste).

*Database lógico:* Redis existente (`redis:6379`) já serve o JuiceFS no db `1`. Este change usa um db lógico diferente (`2`) na mesma instância — evita provisionar um segundo Redis num projeto que já roda 100% local; databases lógicos do Redis isolam os keyspaces sem overhead de infra novo.

### D4 — Token opaco = identificador aleatório de alta entropia

Gerado com `SecureRandom` (ex. 32 bytes, codificado base64url) — não é um JWT, não é previsível, não carrega nenhuma informação decodificável pelo cliente. Isso é o próprio ponto do padrão: o token não significa nada fora do Redis desta API.

### D5 — Contrato via interface

`PhantomTokenIssuer` (nome sugerido, pacote `api/internal/auth`) com métodos equivalentes a `issue(username, password) -> PhantomToken` e `revoke(token) -> void`. O controller HTTP depende só dessa interface — trocar a implementação (ex. outra estratégia de token store, ou um v2 do fluxo) não toca o controller.

### D6 — ROPC é aceito conscientemente

O login endpoint recebe usuário+senha do usuário final e repassa como `grant_type=password` ao Keycloak — é o fluxo Resource Owner Password Credentials, considerado anti-padrão para APIs públicas modernas (cliente teria que confiar credenciais a um backend em vez de usar redirect/PKCE). Aceito aqui porque (a) já é exatamente o que `KeycloakTokens.java` faz em teste, (b) o projeto é 100% local/aprendizado, sem exposição pública, (c) o objetivo do change é o padrão Phantom Token em si, não uma reforma do grant type. Se o projeto algum dia expuser a API publicamente, isso precisa ser revisitado (authorization code + PKCE).

## Risks / Trade-offs

- **[Risco] Redis indisponível derruba toda autenticação de usuário** (login e toda requisição autenticada dependem do lookup) → Mitigação: Redis já é dependência dura do JuiceFS hoje (sem ele o storage nem monta) — não é uma dependência nova no grafo de disponibilidade do projeto, só um uso a mais da mesma instância.
- **[Risco] Sessão "zumbi": usuário deslogado (Redis limpo) mas o JWT real ainda válido no Keycloak** → Mitigação: por design, o JWT real nunca é aceito diretamente nas rotas de usuário depois deste change (só o token opaco é aceito nesse caminho) — revogar no Redis já basta.
- **[Risco] `grant_type=password` sem rate limiting no login vira alvo de força bruta** → Mitigação: fora de escopo deste change (ver Non-Goals); registrar como débito para o Épico 9 mais amplo.
- **[Trade-off] Guardar só claims (não o JWT bruto) no Redis** significa que, se o formato de claims mudar no realm, tokens já emitidos ficam com o formato antigo até expirar (TTL curto, mesmo problema de qualquer cache — aceitável).

## Migration Plan

Aditivo, sem remover nada: rotas continuam aceitando JWT direto (MCP/service accounts inalterado); usuário final passa a ter a opção de logar via `/api/v1/auth/login` em vez de falar com o Keycloak diretamente — nenhum cliente existente quebra, o `.http` de Keycloak (`docs/http/keycloak/01-obter-token.http`) continua funcionando para debug/dev. Rollback = remover o pacote `auth` e as rotas; resource server volta a só aceitar JWT (comportamento anterior inalterado no código).

## Open Questions

1. Nome definitivo do(s) endpoint(s) e do path (`/api/v1/auth/login` + `/logout` — confirmar convenção de path com o usuário, hoje só existe `/api/v1/documents`).
2. ~~Confirmar D1 (módulo novo vs. pacote)~~ — **resolvido em 2026-07-20: pacote `api/internal/auth/`** (ver D1 revisado acima).
3. Vale um ADR novo (`docs/adr/ADR-004-...`) para D2 (resolução dual de token), dado o peso arquitetural, ou fica só registrado aqui em design.md? (ADL-008 em `sdd.md` já é "descoberta, ADR pendente" — pode ser o mesmo ADR endereçando os dois).
4. Logout entra neste change ou vira uma tarefa separada — usuário mencionou "podemos ter mais de um endpoint" sem fechar quais.
