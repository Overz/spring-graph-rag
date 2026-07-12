# language: pt
@pendente
Funcionalidade: Recuperação híbrida de contexto via MCP
  Cobre RF25 (busca combinada em grafo e vetor com filtros de metadados e
  fusão via Reciprocal Rank Fusion) e RF33 (avaliação de qualidade contra
  golden set).

  @RF25 @MCP @ContextRetrieve
  Cenário: Busca unificada via Model Context Protocol
    Dado que um agente LLM aciona a ferramenta MCP de busca por grafos para o termo "Microsserviços"
    Quando a ferramenta consultar o Neo4j validando a flag de nós ativos
    Então ela deve resgatar as propriedades "openSearchId" dos nós "Chunk" associados a essa entidade
    E deve realizar uma busca por IDs no OpenSearch para extrair o texto original
    E deve compor o retorno unificado (topologia do grafo + blocos de texto) para a LLM

  @RF25
  Cenário: Filtros de metadados obrigatórios aplicados nas duas buscas
    Dado que o usuário "dev_user" do tenant "acme_inc" executa uma consulta via ferramenta MCP
    Quando a recuperação híbrida for executada
    Então a query Cypher deve aplicar os filtros "tenantId = acme_inc", "ownerId = dev_user" e "isActive = true"
    E a busca vetorial deve aplicar os mesmos filtros de metadados

  @RF25
  Cenário: Buscas vetorial e de grafo executadas em paralelo
    Dado que uma consulta foi recebida pela ferramenta MCP de recuperação
    Quando a recuperação híbrida iniciar
    Então a busca vetorial top-N por similaridade e a travessia de grafo devem ser executadas em paralelo
    E a fusão dos resultados deve ocorrer somente após as duas buscas concluírem

  @RF25
  Cenário: Travessia de grafo limitada para evitar explosão de contexto
    Dado que a consulta menciona a entidade "Microsserviços" presente no grafo
    Quando a travessia do grafo partir dessa entidade
    Então a profundidade da travessia deve ser limitada a no máximo 2 hops

  @RF25
  Cenário: Fusão dos rankings via Reciprocal Rank Fusion
    Dado que a busca vetorial retornou o ranking:
      | posicao | chunk   |
      | 1       | chunk-A |
      | 2       | chunk-B |
      | 3       | chunk-C |
    E a travessia de grafo retornou o ranking:
      | posicao | chunk   |
      | 1       | chunk-C |
      | 2       | chunk-A |
    Quando a fusão via RRF for aplicada com "k = 60"
    Então o score de cada chunk deve ser a soma de "1 / (k + rank)" em cada lista em que aparece
    E o chunk "chunk-A" deve ranquear acima do chunk "chunk-B", pois aparece bem posicionado nas duas listas

  @RF33
  Cenário: Avaliação da recuperação contra o golden set
    Dado que existe um conjunto de consultas de referência com os chunks esperados como relevantes
    Quando a avaliação de qualidade de recuperação for executada
    Então as métricas de precisão e recall devem ser calculadas para cada consulta de referência
    E o resultado da abordagem GraphRAG deve poder ser comparado com a recuperação puramente vetorial
