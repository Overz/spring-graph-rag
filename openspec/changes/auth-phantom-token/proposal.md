## Why

Hoje o usuário obtém o JWT direto do Keycloak (client `graphrag-api`, grant `password`) e envia esse JWT cru pra API — o token com todas as claims trafega e vive no cliente até expirar, sem possibilidade de revogação imediata (o token é self-contained; matar sessão no Keycloak não invalida um JWT já emitido antes do fim do seu `exp`). O padrão **Phantom Token** resolve isso para o fluxo de usuário: a API emite um token opaco (sem claims) que o cliente passa a usar; a cada requisição, a API troca esse opaco pelo JWT real guardado no Redis — revogação vira um `DEL` no Redis, e nenhuma claim sensível chega ao cliente. RF35 já exige autenticação via Keycloak/JWT; este change endurece esse fluxo especificamente para o login de usuário final (não para os service accounts client_credentials do MCP, que continuam recebendo o JWT do Keycloak diretamente — ver Impact).

## What Changes

- Novo(s) endpoint(s) de autenticação de usuário: `POST /api/v1/auth/login` (emite phantom token) e `POST /api/v1/auth/logout` (revoga). Realm (`graphrag`) e `grant_type` (`password`) fixos — só autenticação de usuário final, não client_credentials.
- App passa a fazer o password grant contra o Keycloak internamente (hoje isso só existe no `KeycloakTokens.java` de teste) e guarda o JWT/claims resultantes no Redis, chaveado pelo token opaco devolvido ao cliente, com TTL = expiração do token real.
- **BREAKING** para o fluxo de resource server: rotas protegidas passam a aceitar o token opaco (phantom) do novo login além do JWT direto do Keycloak (necessário porque os service accounts MCP/client_credentials continuam usando JWT cru — RF35 exige autenticação idêntica pra REST e MCP, mas a origem do token é diferente por tipo de chamador). O `SecurityConfig` precisa resolver os dois formatos.
- Contrato do(s) endpoint(s) exposto via interface (ex. `PhantomTokenIssuer`), preparando espaço para trocar a estratégia de token store ou adicionar uma versão futura sem tocar no controller.
- Novo pacote `api/internal/auth/` (exceção pontual ao layout por camada, mesmo tratamento que `shared` já recebe — ver `design.md` D1; decisão de manter dentro de `api`, sem módulo Spring Modulith novo, porque `mcp` nunca faz login de usuário — só client_credentials).
- Nova dependência `spring-boot-starter-data-redis`; reuso da instância Redis já existente no `compose.yaml` (hoje só metadata do JuiceFS, database lógico `1`) num database lógico diferente.

## Capabilities

### New Capabilities
- `auth-phantom-token`: emissão e revogação de token opaco (phantom token) para login de usuário final via Keycloak (realm `graphrag`, grant `password`), backed por Redis; contrato via interface para implementações futuras.

### Modified Capabilities
- `autenticacao`: o requirement "Autenticação JWT obrigatória na API" passa a admitir dois formatos de token válido nas rotas de usuário — o JWT direto do Keycloak (client_credentials/MCP, comportamento inalterado) e o token opaco emitido por `auth-phantom-token` (resolvido internamente pela API via Redis antes da validação). Os requirements de claim `tenantId` obrigatória e `CallerContext` resolvido só das claims continuam valendo idênticos, agora alimentados também pelas claims recuperadas do Redis.

## Impact

- **Código:** `SecurityConfig`/`CallerContextJwtConverter` (api) ganham um caminho de resolução dual (JWT vs. opaco); novo pacote `api/internal/auth/` com o(s) controller(s), o cliente HTTP pro Keycloak (password grant) e o repositório Redis do phantom token.
- **Dependências:** `spring-boot-starter-data-redis` no `pom.xml`; `TestcontainersConfiguration` ganha container Redis (hoje só Postgres/Neo4j/Keycloak).
- **Infra:** nenhuma mudança em `compose.yaml` — reusa o serviço `redis` já existente, com database lógico diferente do usado pelo JuiceFS.
- **Docs:** `docs/sdd/seguranca.md` §1 (novo fluxo), `CLAUDE.md` (Package layout — `auth/` como segunda exceção pontual ao lado de `shared`), `docs/rag-plan.md` ([9.4]), `docs/http/auth/*.http` novos, possível ADR novo pra estratégia de resolução dual de token (D2).
- **Fora de escopo deste change:** MCP continua recebendo JWT direto (client_credentials) — não migra pra phantom token aqui; refresh token e múltiplas sessões concorrentes por usuário ficam para um change futuro, se necessário.
