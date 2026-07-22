# language: pt
Funcionalidade: Listagem paginada de documentos
  Cobre RF40 (listagem paginada, compartilhada entre os usuários do tenant, com filtro
  opcional de documentos excluídos logicamente).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF40
  Cenário: Listagem traz apenas documentos ativos por padrão
    Dado que o "dev_user" enviou o arquivo "ativo.pdf"
    E que o "dev_user" enviou o arquivo "sera-excluido.pdf"
    E o "dev_user" comandar a exclusão do "sera-excluido.pdf"
    Quando o usuário listar os documentos do tenant
    Então a listagem deve conter o arquivo "ativo.pdf"
    E a listagem não deve conter o arquivo "sera-excluido.pdf"

  @RF40
  Cenário: Listagem com includeInactive também traz documentos excluídos
    Dado que o "dev_user" enviou o arquivo "ativo.pdf"
    E que o "dev_user" enviou o arquivo "sera-excluido.pdf"
    E o "dev_user" comandar a exclusão do "sera-excluido.pdf"
    Quando o usuário listar os documentos do tenant incluindo inativos
    Então a listagem deve conter o arquivo "ativo.pdf"
    E a listagem deve conter o arquivo "sera-excluido.pdf"
    E o item "sera-excluido.pdf" da listagem deve ter status "DELETED" e active "false"
    E o item "ativo.pdf" da listagem deve ter status "UPLOADED" e active "true"

  @RF40
  Cenário: Listagem é compartilhada entre usuários do mesmo tenant
    Dado que o "dev_user" enviou o arquivo "meu-arquivo.pdf"
    E que o "outra_pessoa" enviou o arquivo "arquivo-do-colega.pdf"
    Quando o usuário listar os documentos do tenant
    Então a listagem deve conter o arquivo "meu-arquivo.pdf"
    E a listagem deve conter o arquivo "arquivo-do-colega.pdf"
    E cada item da listagem deve informar quem enviou

  @RF40 @RF30
  Cenário: Listagem nunca inclui documentos de outro tenant
    Dado que o "dev_user" enviou o arquivo "documento-acme.pdf"
    E que o "usuario_globex" enviou o arquivo "documento-globex.pdf"
    Quando o usuário listar os documentos do tenant
    Então a listagem deve conter o arquivo "documento-acme.pdf"
    E a listagem não deve conter o arquivo "documento-globex.pdf"

  @RF40
  Cenário: Listagem é paginada
    Dado que o "dev_user" enviou 3 arquivos distintos
    Quando o usuário listar os documentos do tenant com tamanho de página 2
    Então a página retornada deve conter 2 itens
    E deve haver mais de uma página de resultado
