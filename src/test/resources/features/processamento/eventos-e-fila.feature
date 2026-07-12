# language: pt
@pendente
Funcionalidade: Processamento assíncrono orientado a eventos
  Cobre RF12 (arquitetura orientada a eventos), RF13 (fila de processamento com
  idempotência, isolamento de falhas e retry) e RF39 (isolamento de carga entre
  tenants — fair queueing).

  @RF12
  Cenário: Publicação de evento interno após persistência inicial
    Dado que o arquivo "ata-reuniao.pdf" foi validado e armazenado com sucesso
    Quando a transação de persistência inicial for concluída
    Então um evento interno de documento enviado deve ser publicado pelo módulo de ingestão
    E o evento deve carregar o identificador do documento, o "tenantId", o "ownerId" e o "correlationId"
    E o processamento deve iniciar sem acoplamento direto entre os módulos

  @RF03 @RF13 @ControleDeCarga @Mensageria
  Cenário: Transição automática para fila ao exceder limite de processamento acumulado
    Dado que a empresa "acme_inc" já possui múltiplos arquivos em processamento totalizando 9MB
    Quando um usuário dessa empresa realizar o upload de um novo arquivo PDF de 3MB
    Então o sistema deve validar que o payload acumulado excederá o teto de 10MB
    E o sistema deve aceitar o upload com status "QUEUED"
    E a etapa de extração deve ser delegada estritamente para a fila de mensageria assíncrona, evitando Out Of Memory (OOM)

  @RF13
  Cenário: Consumo idempotente de eventos duplicados
    Dado que o evento de processamento do documento "doc-777" foi publicado
    Quando o mesmo evento for entregue duas vezes ao consumidor
    Então o processamento do documento "doc-777" deve ser executado uma única vez
    E a segunda entrega deve ser reconhecida e descartada sem efeitos colaterais

  @RF13
  Cenário: Isolamento de falhas entre documentos na fila
    Dado que os documentos "doc-111" e "doc-222" estão na fila de processamento
    Quando o processamento do documento "doc-111" falhar
    Então o processamento do documento "doc-222" deve continuar normalmente
    E a falha do documento "doc-111" não deve bloquear a fila

  @RF39
  Cenário: Tenant com alto volume não monopoliza a capacidade de processamento
    Dado que o tenant "mega_corp" possui 100 arquivos aguardando na fila de processamento
    E o tenant "acme_inc" enviou 1 arquivo logo em seguida
    Quando os workers de processamento consumirem a fila
    Então o arquivo do tenant "acme_inc" não deve aguardar o esvaziamento da fila do tenant "mega_corp"
    E o limite de concorrência por tenant deve ser respeitado
