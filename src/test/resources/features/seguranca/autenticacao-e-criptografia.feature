# language: pt
Funcionalidade: Autenticação e criptografia
  Cobre RF35 (autenticação OAuth2/OIDC com JWT para usuários e ferramentas
  MCP, criptografia em trânsito e em repouso) e o endurecimento do login de
  usuário via Phantom Token (ADR-004, change auth-phantom-token).
  Os cenários de token fecharam no Épico 0 ([0.7]); MCP e criptografia
  seguem @pendente até o Épico 9 ([9.4]/[9.5]).

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
  Cenário: Login com credenciais válidas emite um token opaco, não um JWT
    Quando o usuário "alice" fizer login com a senha correta
    Então o login deve ser aceito e o token devolvido não deve ter formato de JWT

  @RF35
  Cenário: Login com senha incorreta é rejeitado
    Quando o usuário "alice" fizer login com a senha "errada"
    Então a resposta deve ser "401 Unauthorized"

  @RF35
  Cenário: Requisição autenticada com o token opaco do login é aceita
    Dado que o usuário "alice" fez login e obteve um token opaco
    Quando a requisição chegar à API usando o token opaco
    Então a resposta não deve ser "401 Unauthorized"

  @RF35
  Cenário: Logout revoga o token opaco
    Dado que o usuário "alice" fez login e obteve um token opaco
    Quando o usuário fizer logout com o token opaco
    E a requisição chegar à API usando o token opaco
    Então a resposta deve ser "401 Unauthorized"

  @RF35
  Cenário: Refresh renova a sessão mantendo o mesmo token opaco
    Dado que o usuário "alice" fez login e obteve um token opaco
    Quando o usuário chamar o endpoint de refresh com o token opaco
    Então o token opaco devolvido deve ser igual ao anterior

  @RF35 @pendente
  Cenário: Chamadas às ferramentas MCP exigem autenticação OAuth2/OIDC
    Dado que um agente externo aciona uma ferramenta MCP sem credenciais válidas
    Quando a chamada for recebida pelo servidor MCP
    Então a chamada deve ser rejeitada por falta de autenticação

  @RF35 @pendente
  Cenário: Criptografia em trânsito e em repouso
    Dado que o sistema está em operação
    Então toda comunicação externa e entre serviços internos deve usar TLS
    E os dados devem estar criptografados em repouso nos seguintes armazenamentos:
      | armazenamento  |
      | Object Storage |
      | PostgreSQL     |
      | Neo4j          |
      | OpenSearch     |
