## ADDED Requirements

> Fatia de **RF35** (identidade). Ainda não existe cenário `.feature` dedicado — este change deve adicionar os cenários abaixo em `src/test/resources/features/seguranca/autenticacao-e-criptografia.feature` (tag `@RF35`) durante a implementação, seguindo `docs/sdd/seguranca.md` §1 e a decisão registrada em `design.md`.

### Requirement: Emissão de phantom token no login de usuário

O sistema SHALL expor um endpoint de login que autentica um usuário final contra o realm `graphrag` do Keycloak com `grant_type=password` fixo, e SHALL devolver ao cliente um token opaco (phantom token) — nunca o JWT real nem qualquer claim do Keycloak. O JWT real e as claims extraídas SHALL ser guardados internamente (Redis), associados ao token opaco, com TTL igual à expiração do token real.

#### Scenario: Login com credenciais válidas devolve token opaco

- **WHEN** um usuário existente no realm `graphrag` faz login com usuário e senha corretos
- **THEN** a resposta traz um token opaco que não é um JWT (não decodificável em claims) e o JWT real correspondente fica armazenado no Redis associado a esse token

#### Scenario: Login com credenciais inválidas é rejeitado

- **WHEN** um usuário faz login com usuário ou senha incorretos
- **THEN** a resposta é `401 Unauthorized` e nenhum token opaco é emitido

### Requirement: Renovação de sessão sem novo login

O sistema SHALL expor um endpoint de refresh que, dado um token opaco válido, troca o `refresh_token` do Keycloak associado (cacheado no Redis) por um novo `access_token`/`refresh_token` via `grant_type=refresh_token` fixo, e SHALL atualizar a mesma entrada no Redis — o token opaco entregue ao cliente no login original NÃO SHALL mudar.

#### Scenario: Refresh de sessão válida mantém o mesmo token opaco

- **WHEN** um usuário autenticado chama o endpoint de refresh antes da sua sessão expirar
- **THEN** a API renova o `access_token`/`refresh_token` reais junto ao Keycloak e o cliente continua usando o mesmo token opaco de antes

#### Scenario: Refresh com token opaco inexistente ou expirado é rejeitado

- **WHEN** o endpoint de refresh é chamado com um token opaco que não existe (ou cuja sessão já expirou) no Redis
- **THEN** a resposta é `401 Unauthorized`

### Requirement: Revogação de phantom token no logout

O sistema SHALL expor um endpoint de logout que revoga um token opaco válido, removendo sua entrada do Redis. Após a revogação, esse token opaco não SHALL mais autenticar nenhuma requisição, independentemente do `exp` do JWT real subjacente no Keycloak.

#### Scenario: Logout revoga o token opaco

- **WHEN** um usuário autenticado chama o endpoint de logout com seu token opaco
- **THEN** a entrada correspondente é removida do Redis

#### Scenario: Requisição com token opaco revogado é rejeitada

- **WHEN** uma requisição à API apresenta um token opaco já revogado (ou nunca emitido)
- **THEN** a resposta é `401 Unauthorized`, sem iniciar nenhum processamento
