# autenticacao Specification

## Purpose

Fatia do **RF35** (identidade desde o dia 1, ADL-008): autenticação JWT obrigatória via Keycloak (realm `graphrag`), claim `tenantId` obrigatória e resolução do `CallerContext` exclusivamente a partir das claims do token. Validada por `src/test/resources/features/seguranca/autenticacao-e-criptografia.feature` (`@RF35`); os cenários de AuthN de MCP e criptografia pertencem ao Épico 9 ([9.4]/[9.5]). Design: `docs/sdd/seguranca.md` §1.

## Requirements

### Requirement: Autenticação JWT obrigatória na API

Toda requisição à API REST SHALL apresentar um JWT válido emitido pelo realm `graphrag` (verificado via JWKS). Requisição sem token, com token inválido ou expirado SHALL ser rejeitada com `401 Unauthorized`, sem iniciar nenhum processamento.

#### Scenario: Requisição sem token

- **WHEN** uma requisição chega à API sem token de autenticação
- **THEN** a resposta é `401 Unauthorized` e nenhum processamento é iniciado

#### Scenario: Token expirado

- **WHEN** uma requisição chega à API com JWT expirado
- **THEN** a resposta é `401 Unauthorized`

### Requirement: Claim tenantId obrigatória no token

Token sintaticamente válido mas **sem a claim `tenantId`** SHALL ser rejeitado com `401 Unauthorized` — identidade sem tenant não existe neste sistema (DoD da [0.7]).

#### Scenario: Token válido sem claim tenantId

- **WHEN** uma requisição chega com JWT válido do realm que não carrega a claim `tenantId`
- **THEN** a resposta é `401 Unauthorized`

### Requirement: CallerContext resolvido exclusivamente das claims

O sistema SHALL materializar um `CallerContext {tenantId, ownerId, roles}` a partir das claims do token (`tenantId` da claim homônima; `ownerId` = claim `sub`; roles de realm granulares por operação). Controllers e tools MCP SHALL enxergar apenas o `CallerContext` — nenhum código de domínio lê JWT cru, e `tenantId`/`ownerId` NUNCA são aceitos do corpo ou de parâmetros da requisição.

#### Scenario: Identidade vem do token, não do corpo

- **WHEN** uma requisição autenticada informa um `tenantId`/`ownerId` divergente no corpo da requisição
- **THEN** o sistema usa exclusivamente os valores das claims do token e ignora os do corpo

#### Scenario: Roles disponíveis no contexto

- **WHEN** um usuário autenticado com roles de realm (ex.: `document:upload`, `query:read`) aciona a API
- **THEN** o `CallerContext` carrega essas roles para as decisões de autorização das camadas seguintes
