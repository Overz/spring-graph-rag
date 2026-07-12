# language: pt
@pendente
Funcionalidade: Geração e armazenamento de embeddings
  Cobre RF19 (geração de representações vetoriais via evento) e RF20
  (armazenamento vetorial no OpenSearch com metadados de filtro).

  @RF19
  Cenário: Disparo de evento para geração de embeddings após o chunking
    Dado que o documento "manual.md" concluiu a etapa "CHUNKING" com 42 chunks filhos
    Quando a etapa "EMBEDDING" iniciar
    Então um evento deve ser enviado para o modelo de embedding processar os chunks
    E cada um dos 42 chunks filhos deve receber sua representação vetorial

  @RF20
  Cenário: Armazenamento dos embeddings com metadados de filtro
    Dado que os embeddings dos chunks do documento "manual.md" foram gerados
    Quando os vetores forem persistidos no datasource vetorial
    Então cada vetor deve ser salvo no OpenSearch com os seguintes metadados:
      | metadado   | proposito                       |
      | chunkId    | correlação com o nó no grafo    |
      | documentId | rastreio do documento de origem |
      | ownerId    | filtro de escopo por usuário    |
      | tenantId   | filtro de isolamento por tenant |
      | isActive   | exclusão lógica na busca        |

  @RF20
  Cenário: Busca vetorial respeita os filtros de metadados
    Dado que existem vetores de documentos dos tenants "acme_inc" e "mega_corp" no OpenSearch
    Quando uma busca vetorial for executada com o filtro "tenantId = acme_inc"
    Então apenas vetores de documentos do tenant "acme_inc" devem ser retornados
