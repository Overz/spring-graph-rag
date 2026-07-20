## MODIFIED Requirements

### Requirement: Autenticação JWT obrigatória na API

Toda requisição à API REST SHALL apresentar uma credencial válida em um dos dois formatos aceitos: um JWT emitido diretamente pelo realm `graphrag` (verificado via JWKS — caminho usado pelos service accounts client_credentials, ex. agentes MCP) ou um token opaco (phantom token) emitido pelo login de usuário (`auth-phantom-token`), resolvido internamente pela API via consulta ao Redis antes de qualquer autorização. Requisição sem credencial, com JWT inválido/expirado, ou com token opaco inexistente/revogado/expirado SHALL ser rejeitada com `401 Unauthorized`, sem iniciar nenhum processamento.

#### Scenario: Requisição sem token

- **WHEN** uma requisição chega à API sem token de autenticação
- **THEN** a resposta é `401 Unauthorized` e nenhum processamento é iniciado

#### Scenario: Token expirado

- **WHEN** uma requisição chega à API com JWT expirado
- **THEN** a resposta é `401 Unauthorized`

#### Scenario: Requisição autenticada com token opaco válido

- **WHEN** uma requisição chega à API com um token opaco válido emitido pelo endpoint de login
- **THEN** a API resolve o JWT/claims reais no Redis e trata a requisição como autenticada, idêntico ao caminho de JWT direto

#### Scenario: Token opaco inexistente ou revogado

- **WHEN** uma requisição chega à API com um token opaco que não corresponde a nenhuma entrada no Redis (nunca emitido, expirado ou revogado)
- **THEN** a resposta é `401 Unauthorized`
