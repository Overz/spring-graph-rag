# Keycloak — identidade do GraphRAG (ADL-008 / [0.7])

Build otimizado do Keycloak (multi-stage, `kc.sh build --db=postgres --health-enabled --metrics-enabled`) + realm `graphrag` versionado como infra-como-código em [`data/import/graphrag-realm.json`](data/import/graphrag-realm.json). Mudar usuário/role/mapper = mudar o JSON commitado. Especificação: `docs/sdd/seguranca.md` §1.

## Subir

```bash
docker compose up -d --build keycloak
```

O compose builda a imagem a partir deste diretório, injeta [.env.keycloak](.env.keycloak) (runtime, DEV) e monta `data/import/` como volume — o realm é importado no primeiro start (`--import-realm`). Console admin: <http://localhost:8080> (`admin`/`admin`).

> O `--import-realm` **não** reimporta realm já existente: após mudar o JSON, recrie o database `keycloak` no Postgres (ou o volume `pgdata`) e suba de novo.

## Realm `graphrag`

- **Roles de realm:** `document:upload`, `document:delete`, `document:reprocess`, `query:read` (usuário comum) + `admin:dlq`, `admin:entity-review`, `admin:lgpd` (admins).
- **Claim `tenantId`:** client scope `tenant` (default do realm, vale para todos os clients) mapeia o atributo de usuário `tenantId` para o access token. `ownerId` = claim `sub`.
- **Clients:** `graphrag-api` (público, password grant em DEV) e um client confidencial por tenant para agentes MCP (`graphrag-mcp-agent-acme` / `graphrag-mcp-agent-globex`, client credentials; o service account carrega o `tenantId` do tenant e a role `query:read`).

### Usuários de teste (senha = username — DEV apenas)

| Tenant | Usuários | Admin |
|---|---|---|
| `acme_inc` | `alice`, `bob` | `admin-acme` |
| `globex` | `carol`, `dave` | `admin-globex` |

## Obter token

Password grant (usuário, client `graphrag-api`):

```bash
curl -s -X POST http://localhost:8080/realms/graphrag/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=graphrag-api \
  -d username=alice \
  -d password=alice | jq -r .access_token
```

Client credentials (agente MCP do tenant `acme_inc`; secret de DEV no realm JSON):

```bash
curl -s -X POST http://localhost:8080/realms/graphrag/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=graphrag-mcp-agent-acme \
  -d client_secret=acme-agent-dev-secret | jq -r .access_token
```

O access token traz `tenantId`, `sub` e `realm_access.roles` — o que o resource server precisa para materializar o `CallerContext`.
