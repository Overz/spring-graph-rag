# Segurança — Identidade, AuthZ, Auditoria, Prompt Injection e LGPD

> Parte do [SDD](../sdd.md). Cobre autenticação e identidade (RF35 + ADL-008), isolamento multitenant (RF30), auditoria imutável (RF31), mitigação de prompt injection (RF34), criptografia (RF35) e direito ao esquecimento (RF36).
> **Features BDD:** `seguranca/multitenant.feature`, `seguranca/auditoria.feature`, `seguranca/prompt-injection.feature`, `seguranca/autenticacao-e-criptografia.feature`, `seguranca/lgpd.feature`.

---

## 1. Identidade — Keycloak + JWT desde o dia 1 (ADL-008)

### 1.1 Topologia

- **Keycloak (última versão)** no `compose.yaml`, seguindo o padrão de referência validado na descoberta: Dockerfile multi-stage com `kc.sh build --db=postgres --health-enabled --metrics-enabled`, configuração em `.env.keycloak`, healthcheck no management port (`/health/ready`), schema próprio no Postgres existente.
- **Realm `graphrag` versionado como JSON** no repositório (`infra/keycloak/data/import/graphrag-realm.json`), importado no start (`--import-realm`, override habilitado). O realm é infra-como-código: mudar usuário/role/mapper = mudar o JSON commitado.

### 1.2 Modelo de tenant no realm (realm único + claim)

- Usuários carregam o **atributo `tenantId`**, exposto no access token por um **protocol mapper** como claim `tenantId`.
- `ownerId` = claim `sub` (identidade estável do usuário no realm).
- **Clients:**
  - `graphrag-api` — client dos usuários (fluxo password/authorization code em dev);
  - `graphrag-mcp-agent` — client confidencial (client credentials) para agentes MCP; o *service account* do client carrega o atributo `tenantId` como qualquer usuário — um agente pertence a um tenant, sem exceção.
- **Roles de realm, granulares por operação** (padrão da referência): `document:upload`, `document:delete`, `document:reprocess`, `query:read`, `admin:dlq`, `admin:entity-review`, `admin:lgpd`. Usuário comum recebe as quatro primeiras; admin recebe as `admin:*`.
- **Realm de teste seedado:** 2 tenants × 2 usuários (ex.: `acme_inc`/`alice`,`bob` e `globex`/`carol`,`dave`) + 1 admin por tenant + 1 client de agente por tenant — o mínimo que os cenários BDD de isolamento exigem.

### 1.3 Validação no app

- Spring Security **OAuth2 Resource Server** (JWT via JWKS do realm). Sem token, token inválido ou expirado → `401`; token válido sem role da operação → `403`. Vale idêntico para REST e para as ferramentas MCP (RF35).
- Um conversor único materializa o `CallerContext {tenantId, ownerId, roles}` das claims — token **sem claim `tenantId` é rejeitado** (401): identidade sem tenant não existe neste sistema.
- Controllers e tools só enxergam `CallerContext`; nenhum código de domínio lê JWT cru.

> **Proposto, ainda não implementado:** `openspec/changes/auth-phantom-token` endurece o login de usuário final com o padrão **Phantom Token** (Redis) — a API emite um token opaco em vez do JWT cru, resolvido internamente a cada requisição; JWT direto continua valendo para os service accounts MCP (client_credentials), inalterado. Vive como pacote `api/internal/auth/` (sem módulo Spring Modulith novo — `mcp` nunca faz login via password grant). Schema Redis e resolução dual de token registrados no `design.md` do change; path dos endpoints e escopo do logout ainda em aberto.

## 2. Isolamento multitenant — AuthZ (RF30)

Três camadas, todas obrigatórias (defesa em profundidade):

| Camada | Regra |
|---|---|
| **Rota** | role da operação exigida (Spring Security) |
| **Serviço** | operações sobre documento validam `tenantId` **e** `ownerId` do `CallerContext` contra o registro — upload, consulta de status, exclusão, reprocessamento, nova versão são do **dono**; consulta (RF25) é do **tenant** com validação de escopo por `ownerId` onde o RF24 exigir |
| **Dados** | filtros `tenantId` (+ `ownerId`, + `isActive`) embutidos nas assinaturas de repositório/busca — não existe método de leitura sem eles (decisão de `extracao-e-vetorial.md` §6 e `knowledge-graph.md` §3) |

- **Recurso de outro tenant/dono responde `404`, não `403`** — `403` confirmaria a existência do recurso (enumeração entre tenants). Consistente com `ingestao.md` §1.
- Cenário BDD central (`@Seguranca @Multitenant`): consulta MCP do tenant B **nunca** recebe fragmento do tenant A — garantido pela camada de dados, não por confiança na rota.

## 3. Auditoria imutável (RF31)

- Tabela `audit_log` (schema em `dados.md`): toda ação de estado — uploads (aceitos **e rejeitados**, incluindo `MALWARE_DETECTED`), soft deletes, reprocessamentos, novas versões, decisões de entity review, reprocessos de DLQ, exclusões LGPD.
- Gravação **síncrona, na mesma transação** da ação — ação sem auditoria não commita.
- Imutabilidade em duas defesas: a role de banco da aplicação recebe apenas `INSERT`/`SELECT` na tabela, e um trigger `BEFORE UPDATE OR DELETE` lança exceção. Tentativa de adulteração falha **e** o erro (com a identidade da conexão) fica registrado em log — atende "rejeitada e registrada".
- Consulta da trilha: API admin somente-leitura, role `admin:*`, sempre filtrada por tenant.

## 4. Prompt injection (RF34)

Conteúdo de documento é, por definição, **não confiável**. Mitigação em três pontos (sinalização, não prevenção absoluta — OWASP LLM01):

1. **Delimitação estrutural em todo prompt** que inclua conteúdo de documento (extração RF21 e geração RF26): blocos `<document source="…">…</document>` + instrução de sistema explícita de que o conteúdo é *dado a analisar, não instrução a seguir* (template em `consulta.md` §4).
2. **Validação de schema na extração:** resposta da LLM fora da ontologia fechada é descartada sem interromper o pipeline (`knowledge-graph.md` §2) — mitiga injeção de entidades falsas.
3. **Sinalização de padrões suspeitos:** heurística barata no chunking (lista mantida em configuração: imperativos dirigidos ao modelo — "ignore as instruções", "you are now", "system prompt" etc., pt e en) marca `injection_flag` no chunk (Postgres + propriedade no grafo). Chunk sinalizado **não é bloqueado** — segue o pipeline e fica visível para revisão via API admin. Falso positivo é aceitável; bloqueio silencioso não.

## 5. Criptografia (RF35) — o que é real × o que é simulado

Honestidade de escopo local — a tabela abaixo **é** o entregável desta parte do RF35:

| Item | Local (este projeto) | Produção real (documentado, não implementado) |
|---|---|---|
| AuthN/JWT | **real** — Keycloak + resource server | idem |
| TLS externo (borda) | **postergado** — HTTP no host local; terminação TLS via proxy (padrão Traefik da referência) é extensão do Épico 9 | obrigatório |
| TLS entre serviços do compose | **simulado** — rede bridge isolada do Docker como fronteira de confiança | mTLS/service mesh |
| Criptografia em repouso | **documentada, não implementada** — exigiria SSE no MinIO (KES), TDE/disk encryption nos bancos; sem valor de aprendizado proporcional ao custo local | volumes/discos criptografados gerenciados |
| Segredos | `.env`/variáveis do compose (dev); **nunca** commitados em código de aplicação | vault/secret manager |

## 6. LGPD — direito ao esquecimento (RF36)

Fluxo distinto do soft delete (RF10) e do GC (RF11): **síncrono, físico e verificável**, motivado por solicitação legal de titular.

1. **Solicitação:** API admin (role `admin:lgpd`): titular identificado por entidade `PERSON` no tenant (nome canônico + aliases).
2. **Localização:** todos os nós `Entity` do titular (incluindo aliases), seus relacionamentos, os `Chunk`s conectados por `MENTIONS` e os vetores correspondentes (`openSearchId`).
3. **Hard delete síncrono, na ordem:** vetores no OpenSearch (por id) → nós/relacionamentos no Neo4j → linhas de `chunks` no Postgres. Documentos afetados são marcados `lgpd_redacted = true` — o documento continua existindo, mas com lacunas deliberadas; o titular saiu, o resto do conteúdo fica.
4. **Verificação:** ao final, queries de contagem nas três bases devem retornar **zero** para o titular; resultado ≠ 0 → a operação **falha explicitamente** (não é fire-and-forget) e pode ser reexecutada.
5. **Auditoria:** registro `LGPD_ERASURE` no `audit_log` com o resultado da verificação (RF31). O log de auditoria **não** guarda o conteúdo removido — guarda o fato da remoção.

Decisão registrada: o arquivo original no storage **não** é removido automaticamente (pode conter conteúdo de outros assuntos e outros titulares); a remoção do raw é decisão do operador na mesma API (flag explícita), auditada separadamente. Alternativa descartada — apagar o documento inteiro sempre: destruiria conteúdo legítimo não relacionado ao titular.

## 7. Decisões registradas nesta seção

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Realm único + claim `tenantId`; agente MCP com tenant via service account | realm por tenant; Organizations | um issuer/decoder; enforcement é da aplicação; simplicidade sem perder o isolamento exigido |
| Roles granulares por operação | roles largas (`user`/`admin`) | padrão da referência validada; autorização legível por rota |
| `404` para recurso de outro tenant | `403` | não confirmar existência entre tenants |
| Auditoria síncrona na transação + imutável por permissão e trigger | tabela comum / async | RF31 exige imutabilidade e completude; async perderia ações em crash |
| Injection: sinalizar sem bloquear | bloquear chunks suspeitos | RF34 pede sinalização; detecção perfeita não existe, bloqueio geraria negação de serviço por falso positivo |
| Criptografia at-rest documentada, não implementada | KES/TDE local | custo alto, aprendizado marginal; a tabela real×simulado é o entregável honesto |
| LGPD: raw storage só com flag explícita | apagar documento inteiro | preservar conteúdo legítimo de outros titulares/assuntos |
