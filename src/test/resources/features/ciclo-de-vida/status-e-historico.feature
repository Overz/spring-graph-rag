# language: pt
Funcionalidade: Ciclo de vida de processamento e histórico por documento
  Cobre RF08 (status obrigatórios do ciclo de vida e fork-join de
  EMBEDDING/GRAPH_BUILDING) e RF09 (consulta de status e histórico completo).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF08
  Cenário: Progressão completa do ciclo de vida no caminho feliz
    Dado que o arquivo "politica-ferias.pdf" foi enviado com sucesso
    Quando o pipeline de processamento executar até a conclusão sem falhas
    Então o documento deve transitar em ordem pelos status:
      | RECEIVED     |
      | VALIDATING   |
      | UPLOADED     |
      | QUEUED       |
      | EXTRACTING   |
      | TRANSFORMING |
      | CHUNKING     |
    E após "CHUNKING" as etapas "EMBEDDING" e "GRAPH_BUILDING" devem ser disparadas em paralelo
    E com ambos os ramos concluídos o status final deve ser "COMPLETED"

  @RF08
  Cenário: Sub-estados independentes dos ramos paralelos
    Dado que o documento "manual.pdf" concluiu a etapa "CHUNKING" com sucesso
    Quando o ramo "EMBEDDING" estiver concluído e o ramo "GRAPH_BUILDING" ainda em execução
    Então o sub-estado "embeddingStatus" deve ser "COMPLETED"
    E o sub-estado "graphStatus" deve ser "RUNNING"
    E o status geral do documento deve ser derivado dos dois sub-estados

  @RF08 @ProcessamentoParalelo @FalhaParcial
  Cenário: Sucesso em Embedding com falha definitiva em Graph Building
    Dado que o "relatorio2.md" concluiu a etapa "CHUNKING" com sucesso
    E as etapas "EMBEDDING" e "GRAPH_BUILDING" são disparadas em paralelo
    Quando a etapa "EMBEDDING" for concluída com sucesso
    E a etapa "GRAPH_BUILDING" falhar definitivamente após esgotar as tentativas de retry
    Então o documento deve ser marcado com status "PARTIALLY_COMPLETED"
    E os chunks do "relatorio2.md" devem permanecer disponíveis para busca vetorial em RF25
    E o registro de falha da etapa "GRAPH_BUILDING" deve ficar disponível para reprocessamento manual

  @RF08
  Cenário: Falha definitiva em ambos os ramos paralelos resulta em FAILED
    Dado que o documento "planilha.csv" concluiu a etapa "CHUNKING" com sucesso
    Quando a etapa "EMBEDDING" falhar definitivamente após esgotar as tentativas de retry
    E a etapa "GRAPH_BUILDING" falhar definitivamente após esgotar as tentativas de retry
    Então o documento deve ser marcado com status "FAILED"

  @RF09
  Cenário: Consulta do status atual de um documento
    Dado que o arquivo "politica-ferias.pdf" está na etapa "EXTRACTING"
    Quando o usuário consultar o status do documento
    Então a resposta deve informar o status atual "EXTRACTING"

  @RF09
  Cenário: Consulta do histórico completo de etapas executadas
    Dado que o arquivo "politica-ferias.pdf" concluiu o processamento com status "COMPLETED"
    Quando o usuário consultar o histórico de processamento do documento
    Então o histórico deve listar todas as etapas executadas em ordem cronológica
    E cada registro do histórico deve conter a etapa, o status resultante e o timestamp de execução

  @RF09
  Cenário: Consulta de status de documento inexistente
    Quando o usuário consultar o status do documento "00000000-0000-0000-0000-000000000000"
    Então a consulta deve ser rejeitada com um erro de "Documento não encontrado"

  @RF09 @RF10
  Cenário: Histórico continua acessível após exclusão lógica, com o evento registrado
    Dado que o arquivo "relatorio-anual.pdf" foi enviado com sucesso
    Quando o "dev_user" comandar a exclusão do "relatorio-anual.pdf"
    E o usuário consultar o histórico de processamento do documento
    Então a consulta deve retornar sucesso mesmo com o documento excluído
    E a última entrada do histórico deve ter status "DELETED" e detail "Documento excluído logicamente"

  @RF09 @RF30
  Cenário: Consulta de histórico de documento de outro usuário retorna não encontrado
    Dado que o "Documento_C" pertence ao usuário "outra_pessoa" do tenant "acme_inc"
    Quando o "dev_user" consultar o histórico do "Documento_C"
    Então a consulta deve ser rejeitada com um erro de "Documento não encontrado"
