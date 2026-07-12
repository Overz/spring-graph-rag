# language: pt
@pendente
Funcionalidade: Retry automático e Dead Letter Queue
  Cobre RF29 (retentativas automáticas para falhas temporárias e DLQ com
  reprocessamento manual para falhas definitivas).

  @RF29 @DLQ @TratamentoDeErros
  Cenário: Falha definitiva encaminhada para Dead Letter Queue
    Dado que o processador falhou ao tentar extrair o texto de um "documento.pdf" corrompido
    E o sistema já esgotou o número máximo de tentativas de reprocessamento (retry)
    Quando a última falha for registrada com o status "EXTRACTION_FAILED"
    Então o evento deve ser roteado para uma Dead Letter Queue (DLQ)
    E o status final do arquivo deve ser marcado como "FAILED"
    E o sistema deve habilitar a opção de "Reprocessamento Manual" para os administradores

  @RF29
  Cenário: Falha temporária é resolvida por retentativas automáticas
    Dado que a etapa "EMBEDDING" do documento "doc-800" falhou por indisponibilidade temporária do provedor
    Quando o mecanismo de retry executar as retentativas automáticas
    E a terceira tentativa for bem-sucedida
    Então o pipeline deve continuar normalmente a partir da etapa "EMBEDDING"
    E o histórico deve registrar as 3 tentativas realizadas

  @RF29
  Cenário: Reprocessamento manual de evento na DLQ por usuário autorizado
    Dado que o evento do documento "doc-900" está na Dead Letter Queue
    E o usuário "admin_user" possui permissão de administração
    Quando o usuário "admin_user" comandar o reprocessamento manual do evento
    Então o evento deve ser reenviado para a fila de processamento
    E o processamento deve retomar a partir da última etapa concluída com sucesso

  @RF29 @RF30
  Cenário: Usuário sem permissão não reprocessa eventos da DLQ
    Dado que o evento do documento "doc-900" está na Dead Letter Queue
    E o usuário "dev_user" não possui permissão de administração
    Quando o usuário "dev_user" tentar comandar o reprocessamento manual do evento
    Então a operação deve ser negada por falta de permissão
