# language: pt
@pendente
Funcionalidade: Reconciliação entre Neo4j e OpenSearch
  Cobre RF38 (verificação periódica de integridade referencial entre o grafo
  e a base vetorial, com autocorreção quando possível).

  @RF38 @Reconciliacao @Consistencia
  Cenário: Detecção de vetor órfão no OpenSearch
    Dado que um vetor no OpenSearch referencia um "chunkId" que não possui mais um nó "Chunk" correspondente ativo no Neo4j
    Quando o job de reconciliação periódica for executado
    Então o sistema deve identificar essa divergência
    E deve registrar a inconsistência para auditoria
    E deve remover o vetor órfão do OpenSearch para preservar a integridade da base vetorial

  @RF38
  Cenário: Chunk ativo sem vetor correspondente é sinalizado para reindexação
    Dado que o nó "Chunk" ativo "chunk-33" no Neo4j referencia um "openSearchId" inexistente no OpenSearch
    Quando o job de reconciliação periódica for executado
    Então o sistema deve identificar essa divergência
    E deve registrar a inconsistência para auditoria
    E deve sinalizar o chunk "chunk-33" para reindexação

  @RF38
  Cenário: Bases consistentes não geram ação corretiva
    Dado que todos os nós "Chunk" ativos possuem vetores correspondentes no OpenSearch
    E todos os vetores possuem nós "Chunk" ativos correspondentes no Neo4j
    Quando o job de reconciliação periódica for executado
    Então nenhuma ação corretiva deve ser executada
    E a execução deve ser registrada como íntegra
