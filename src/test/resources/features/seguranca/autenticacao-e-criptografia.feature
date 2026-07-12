# language: pt
@pendente
Funcionalidade: Autenticação e criptografia
  Cobre RF35 (autenticação OAuth2/OIDC com JWT para usuários e ferramentas
  MCP, criptografia em trânsito e em repouso).

  @RF35
  Cenário: Requisição sem token de autenticação é rejeitada
    Dado uma requisição de upload sem token de autenticação
    Quando a requisição chegar à API
    Então a resposta deve ser "401 Unauthorized"
    E nenhum processamento deve ser iniciado

  @RF35
  Cenário: Token expirado é rejeitado
    Dado uma requisição com token JWT expirado
    Quando a requisição chegar à API
    Então a resposta deve ser "401 Unauthorized"

  @RF35
  Cenário: Chamadas às ferramentas MCP exigem autenticação OAuth2/OIDC
    Dado que um agente externo aciona uma ferramenta MCP sem credenciais válidas
    Quando a chamada for recebida pelo servidor MCP
    Então a chamada deve ser rejeitada por falta de autenticação

  @RF35
  Cenário: Criptografia em trânsito e em repouso
    Dado que o sistema está em operação
    Então toda comunicação externa e entre serviços internos deve usar TLS
    E os dados devem estar criptografados em repouso nos seguintes armazenamentos:
      | armazenamento  |
      | Object Storage |
      | PostgreSQL     |
      | Neo4j          |
      | OpenSearch     |
