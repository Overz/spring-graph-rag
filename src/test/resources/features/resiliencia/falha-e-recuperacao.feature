# language: pt
@pendente
Funcionalidade: Falha por etapa e recuperação parcial
  Cobre RF27 (tratamento isolado de falhas por fase com recuperação parcial)
  e RF28 (observabilidade dos erros e rastreabilidade ponta a ponta via
  correlationId).

  @RF27 @RF29 @Resiliencia @RecuperacaoParcial
  Cenário: Falha na API de Embedding e recuperação parcial do estado
    Dado que o processamento do "relatorio.md" atingiu com sucesso o status "CHUNKING"
    E o sistema disparou o evento interno para iniciar a etapa de "EMBEDDING"
    Quando a provedora de LLM/Embedding retornar um erro de "Rate Limit" (HTTP 429)
    Então o sistema deve registrar a falha no histórico com o status "EMBEDDING_FAILED"
    E o mecanismo de retry deve ser acionado
    E na próxima tentativa, o sistema deve retomar a operação a partir do "EMBEDDING", aproveitando os chunks já salvos no banco

  @RF27
  Esquema do Cenário: Cada etapa registra um status de falha específico
    Dado que o documento "doc-500" está na etapa "<etapa>"
    Quando ocorrer uma falha definitiva nessa etapa
    Então a falha deve ser registrada com o status "<status_falha>"

    Exemplos:
      | etapa          | status_falha          |
      | EXTRACTING     | EXTRACTION_FAILED     |
      | TRANSFORMING   | TRANSFORMATION_FAILED |
      | CHUNKING       | CHUNKING_FAILED       |
      | EMBEDDING      | EMBEDDING_FAILED      |
      | GRAPH_BUILDING | GRAPH_BUILDING_FAILED |

  @RF27
  Cenário: Recuperação parcial não reprocessa etapas já concluídas
    Dado que o documento "doc-600" concluiu com sucesso as etapas "EXTRACTING", "TRANSFORMING" e "CHUNKING"
    E a etapa "EMBEDDING" falhou
    Quando o reprocessamento for executado
    Então o processamento deve retomar diretamente na etapa "EMBEDDING"
    E as etapas "EXTRACTING", "TRANSFORMING" e "CHUNKING" não devem ser reexecutadas

  @RF28
  Cenário: Registro estruturado e completo de cada erro
    Dado que a etapa "EXTRACTING" do documento "doc-700" falhou na tentativa 2
    Quando o erro for registrado
    Então o registro deve conter os seguintes campos:
      | campo                  |
      | etapa                  |
      | código do erro         |
      | mensagem               |
      | timestamp              |
      | número da tentativa    |
      | payload de diagnóstico |

  @RF28
  Cenário: correlationId propagado de ponta a ponta
    Dado que o upload do arquivo "auditoria.pdf" gerou o correlationId "corr-42"
    Quando o documento atravessar todas as etapas do pipeline
    Então todos os eventos internos publicados devem carregar o correlationId "corr-42"
    E todos os logs de todas as etapas devem incluir o correlationId "corr-42"
