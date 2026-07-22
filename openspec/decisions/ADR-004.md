# ADR-004: Login de Usuário via Phantom Token (Redis), Endpoints por Ação, Sem Módulo Novo

## Status
Aceito

## Contexto

RF35 (autenticação) já exige JWT via Keycloak desde o dia 1 (ADL-008, `sdd/seguranca.md` §1). Hoje o usuário final obtém esse JWT direto do Keycloak (client `graphrag-api`, `grant_type=password`) e envia o JWT cru pra API — o token é self-contained e vive até seu `exp`; revogar a sessão no Keycloak não invalida um JWT já emitido. `openspec/changes/auth-phantom-token` propõe o padrão **Phantom Token**: a API emite um token opaco pro usuário e resolve internamente o JWT real (via Redis) a cada requisição, permitindo revogação imediata e nunca expondo claims ao cliente.

Três decisões desse change tinham peso arquitetural suficiente pra merecer registro formal, além do `design.md` do change:

1. Onde esse novo fluxo deveria viver (módulo Spring Modulith novo `auth`, ou pacote dentro do módulo `api` já existente).
2. Como o resource server (`SecurityConfig`) resolve dois formatos de token simultaneamente (JWT direto pros service accounts MCP, token opaco pros usuários).
3. Como nomear os endpoints e desenhar o fluxo de renovação de sessão (refresh) sem forçar o usuário a logar de novo.

## Decisão

**Localização:** o fluxo de login/refresh/logout vive em `api/internal/auth/` — pacote dentro do módulo `api` já existente, não um módulo Spring Modulith novo. Login é, na prática, só mais um HTTP entry point de usuário final (o que `api` já faz). `auth/` é tratado como uma segunda exceção pontual ao layout por camada do projeto (`configs/controllers/services/.../errors`), do mesmo jeito que `shared` já é uma exceção documentada.

**Resolução de token no resource server:** um resolvedor decide pelo formato do token no `Authorization` header — JWT tem 2 pontos (`header.payload.signature`) e segue o caminho já existente (`NimbusJwtDecoder` + `CallerContextJwtConverter`, inalterado); qualquer outro valor é tratado como token opaco e resolvido via consulta ao Redis, convergindo no mesmo `CallerContext` final.

**Endpoints, por ação:** `POST /api/v1/auth/login` (`grant_type=password` fixo), `POST /api/v1/auth/refresh` (`grant_type=refresh_token` fixo — renova a sessão junto ao Keycloak sem que o token opaco do cliente mude) e `POST /api/v1/auth/logout` (revoga, apagando a entrada no Redis).

**Schema Redis:** chave `phantom-token:{tokenOpaco}`; valor = claims extraídas do access token (`tenantId`, `ownerId`/`sub`, `roles`) + o `refresh_token` do Keycloak (necessário pro endpoint de refresh); TTL = `refresh_expires_in` (não o `expires_in` do access token — precisa sobreviver até a janela de renovação).

## Alternativas Consideradas

- **Módulo Spring Modulith novo `auth`** (sibling de `api`/`rag`/`mcp`/`chat`/`shared`): rejeitada. O argumento original ("MCP pode reusar o mesmo mecanismo depois") não se sustenta — `mcp` autentica exclusivamente via service account `client_credentials`, nunca via `grant_type=password`; não existe consumidor futuro real pro módulo. O contrato via interface pedido pelo usuário (`PhantomTokenIssuer`) já é atendido por uma interface simples dentro do módulo, no mesmo padrão de `DocumentStorage` (`shared`) e `MalwareScanner` (`api`).
- **`oauth2ResourceServer().opaqueToken(...)` com introspecção real (RFC 7662) a cada requisição**: rejeitada — reintroduz round-trip ao Keycloak por requisição, exatamente o custo que o Redis existe pra evitar.
- **Endpoints nomeados por ator** (ex. `/api/v1/auth/customer/login`): cogitada e descartada. O vocabulário do projeto (`requisitos.md`, `sdd/seguranca.md`, BDD) nunca usa "cliente"/"customer" — sempre "usuário"; e não existe hoje um segundo ator real que passaria por este endpoint (mesmo raciocínio que derrubou o módulo novo). Path aceita um prefixo por ator no futuro, sem custo de migração, se um segundo ator real aparecer.
- **Rotação do token opaco a cada refresh**: rejeitada por agora — mais simples pro cliente manter um único identificador de sessão; pode virar endurecimento futuro se análise de risco pedir.
- **`grant_type=password` (Resource Owner Password Credentials) revisitado como anti-padrão**: aceito conscientemente — já é o que `KeycloakTokens.java` faz em teste, projeto é 100% local/aprendizado sem exposição pública. Revisitar (authorization code + PKCE) se a API for exposta publicamente algum dia.

## Consequências

### Positivas
- Zero mudança em `Application.java`/`ModularityTest`/`package-info.java` além do que `api` já declara — nenhuma fronteira de módulo nova pra manter.
- Cliente nunca vê claims do Keycloak; revogação (logout) é imediata via `DEL` no Redis, independente do `exp` do JWT real.
- Sessão longa sem re-login: `/refresh` estende a mesma entrada no Redis, token opaco do cliente nunca muda.
- MCP (client_credentials) e o fluxo de debug via Keycloak direto (`docs/http/keycloak/01-obter-token.http`) continuam funcionando sem alteração — mudança é aditiva.

### Negativas
- `SecurityConfig` ganha complexidade real: resolver dois formatos de token em vez de um.
- `refresh_token` do Keycloak guardado no Redis amplia o que um vazamento de Redis expõe (antes só claims, agora também um credential reutilizável) — aceito por Redis ser infraestrutura interna sem exposição externa; revisitar se o Épico 9.5 (criptografia em repouso) cobrir isso.
- Nenhum rate limiting no login nesta primeira versão — risco de força bruta registrado como débito pro Épico 9 mais amplo, não bloqueia este change.

## Plano de Ação

Ver `openspec/changes/auth-phantom-token/tasks.md` para o detalhamento — resumo:

1. `spring-boot-starter-data-redis` no `pom.xml`; Redis database lógico novo (reusa a instância existente do `compose.yaml`, hoje só JuiceFS no db `1`).
2. Pacote `api/internal/auth/`: `PhantomTokenIssuer` (interface), implementação do password/refresh_token grant contra o Keycloak, repositório Redis, gerador de token opaco.
3. `SecurityConfig`: resolvedor dual de token (JWT vs. opaco) convergindo no mesmo `CallerContext`.
4. Controllers `POST /api/v1/auth/{login,refresh,logout}`.
5. Cenários `@RF35` novos em `seguranca/autenticacao-e-criptografia.feature`; `.http` novos em `docs/http/auth/`.
