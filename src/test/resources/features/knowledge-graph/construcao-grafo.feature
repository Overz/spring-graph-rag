# language: pt
@pendente
Funcionalidade: Construção do grafo de conhecimento
  Cobre RF22 (criação de nós e relacionamentos no Neo4j), RF23 (integração
  vetor-grafo via openSearchId) e RF24 (escopo global por tenant vs. local por
  usuário).

  @RF22
  Cenário: Criação dos nós e relacionamentos no banco de grafos
    Dado que o documento "manual.pdf" gerou 10 chunks e as entidades "Docker" e "Kubernetes"
    Quando a etapa "GRAPH_BUILDING" for executada
    Então um nó "Document" deve ser criado para o documento
    E um nó "Chunk" deve ser criado para cada chunk
    E um nó "Entity" deve ser criado para cada entidade nova
    E os relacionamentos entre documento, chunks e entidades devem ser criados no Neo4j

  @RF23
  Cenário: Todo nó Chunk armazena a referência openSearchId
    Dado que o chunk "chunk-77" foi indexado no OpenSearch com o identificador "os-abc-123"
    Quando o nó "Chunk" correspondente for criado no Neo4j
    Então o nó deve armazenar obrigatoriamente a propriedade "openSearchId" com o valor "os-abc-123"

  @RF23
  Cenário: Persistência de Chunk sem openSearchId é rejeitada
    Dado que um nó "Chunk" está prestes a ser persistido sem a propriedade "openSearchId"
    Quando a etapa "GRAPH_BUILDING" tentar gravá-lo
    Então a gravação deve ser rejeitada
    E o chunk deve ser sinalizado para reindexação

  @RF24
  Cenário: Insights globais orientados pelo tenantId
    Dado que o tenant "acme_inc" possui documentos de vários usuários formando comunidades de temas
    Quando uma análise global de comunidades for executada para o tenant "acme_inc"
    Então o agrupamento deve considerar todos os documentos ativos do tenant
    E nenhum dado de outros tenants deve participar da análise

  @RF24
  Cenário: Consulta restrita valida rigorosamente o ownerId
    Dado que o usuário "dev_user" executa uma consulta em escopo local
    Quando a query de grafo for montada
    Então o filtro "ownerId = dev_user" deve ser aplicado rigorosamente
