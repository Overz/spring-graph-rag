# language: pt
Funcionalidade: Validação inicial de arquivos enviados
  Cobre RF02 (validações iniciais e verificação de malware), RF04 (tipos de
  arquivo suportados) e RF07 (idempotência por hash SHA-256).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF04
  Esquema do Cenário: Aceitação de todos os tipos de arquivo suportados
    Dado um arquivo "<arquivo>" do tipo "<mime>" com tamanho de "100KB"
    Quando o sistema executar as validações iniciais de ingestão
    Então o arquivo deve ser aprovado na validação de tipo
    E o documento deve prosseguir para o status "UPLOADED"

    Exemplos:
      | arquivo      | mime             |
      | doc.pdf      | application/pdf  |
      | foto.jpg     | image/jpeg       |
      | foto.jpeg    | image/jpeg       |
      | print.png    | image/png        |
      | dados.csv    | text/csv         |
      | payload.json | application/json |
      | config.xml   | application/xml  |
      | notas.txt    | text/plain       |
      | leiame.md    | text/markdown    |

  @RF04 @Validacao @Limites
  Cenário: Rejeição de arquivo com formato não suportado
    Dado que o usuário "dev_user" tenta realizar o upload de um arquivo chamado "treinamento.mp4"
    Quando o sistema executar as validações iniciais de ingestão
    Então a requisição deve ser rejeitada com um erro de "Tipo MIME não suportado"
    E o arquivo não deve ser salvo no Object Storage em "/raw"

  @RF04
  Esquema do Cenário: Rejeição de outros formatos não suportados
    Dado um arquivo "<arquivo>" do tipo "<mime>" com tamanho de "100KB"
    Quando o sistema executar as validações iniciais de ingestão
    Então a requisição deve ser rejeitada com um erro de "Tipo MIME não suportado"

    Exemplos:
      | arquivo       | mime                                                              |
      | video.mp4     | video/mp4                                                         |
      | musica.mp3    | audio/mpeg                                                        |
      | app.exe       | application/vnd.microsoft.portable-executable                     |
      | planilha.xlsx | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |
      | script.sh     | application/x-sh                                                  |

  @RF02
  Cenário: Rejeição de arquivo cujo conteúdo real não corresponde à extensão
    Dado um arquivo "fatura.pdf" cujo conteúdo real é do tipo "application/x-msdownload"
    Quando o sistema executar as validações iniciais de ingestão
    Então o sistema deve detectar o tipo MIME real pelo conteúdo, não pela extensão
    E a requisição deve ser rejeitada com um erro de "Tipo MIME não suportado"

  @RF02
  Cenário: Rejeição de arquivo vazio
    Dado um arquivo "vazio.pdf" do tipo "application/pdf" com tamanho de "0KB"
    Quando o sistema executar as validações iniciais de ingestão
    Então o upload deve ser rejeitado com o motivo "Arquivo vazio"

  @RF02
  Cenário: Rejeição de nome de arquivo com caracteres de path traversal
    Dado um arquivo "../../etc/senhas.pdf" do tipo "application/pdf" com tamanho de "100KB"
    Quando o sistema executar as validações iniciais de ingestão
    Então o upload deve ser rejeitado com o motivo "Nome de arquivo inválido"

  @RF02
  Cenário: Rejeição de arquivo corrompido na verificação de integridade
    Dado um arquivo "corrompido.pdf" do tipo "application/pdf" cujo conteúdo está truncado e ilegível
    Quando o sistema executar as validações iniciais de ingestão
    Então o upload deve ser rejeitado com o motivo "Arquivo corrompido"

  @RF02
  Cenário: Rejeição de arquivo com malware detectado na varredura antivírus
    Dado um arquivo "boleto.pdf" do tipo "application/pdf" contendo a assinatura de teste EICAR
    Quando o sistema submeter o arquivo à varredura antimalware
    Então o upload deve ser rejeitado com o motivo "MALWARE_DETECTED"
    E a rejeição não deve consumir cota de reprocessamento do usuário
    E o arquivo não deve ser salvo no Object Storage em "/raw"

  @RF02
  Cenário: Arquivo limpo é liberado após a varredura antimalware
    Dado um arquivo "limpo.pdf" do tipo "application/pdf" com tamanho de "1MB"
    Quando o sistema submeter o arquivo à varredura antimalware
    Então a varredura não deve encontrar ameaças
    E o documento deve prosseguir para o status "UPLOADED"

  @RF07 @Idempotencia
  Cenário: Tentativa de upload de arquivo idêntico já processado
    Dado que o usuário "dev_user" do tenant "acme_inc" já enviou o arquivo "arquitetura.pdf" com sucesso
    E o hash SHA-256 computado foi "8f434346648f5b96df89ec301c3b7a5a"
    Quando o usuário submeter o mesmo arquivo "arquitetura.pdf"
    Então o sistema deve interceptar a requisição na validação inicial
    E deve rejeitar a operação para evitar duplicação de entidades e vetores
    E nenhum novo evento do ciclo de vida deve ser publicado

  # Permanece @pendente: o comando explícito de reprocessamento (/reprocess) exige um
  # pipeline a reexecutar — nasce com os Épicos 2+ (ver change epico-1, proposal).
  @RF07 @pendente
  Cenário: Reprocessamento explícito de arquivo já processado é permitido
    Dado que o usuário "dev_user" do tenant "acme_inc" já enviou o arquivo "arquitetura.pdf" com sucesso
    Quando o usuário submeter o mesmo arquivo "arquitetura.pdf" com o comando explícito de reprocessamento
    Então o sistema deve aceitar a operação
    E o pipeline completo deve ser reexecutado para o documento

  @RF07
  Cenário: Mesmo conteúdo enviado por outro usuário não é tratado como duplicata
    Dado que o usuário "dev_user" do tenant "acme_inc" já enviou o arquivo "onboarding.pdf" com sucesso
    Quando o usuário "outro_user" do tenant "acme_inc" submeter um arquivo com o mesmo hash SHA-256
    Então o sistema deve aceitar o upload
    E a idempotência deve considerar o escopo do usuário e tenant proprietários

  @RF07
  Cenário: Reenvio de arquivo cujo processamento anterior falhou não é bloqueado
    Dado que o usuário "dev_user" do tenant "acme_inc" enviou o arquivo "planta.pdf" e o processamento terminou com status "FAILED"
    Quando o usuário submeter o mesmo arquivo "planta.pdf"
    Então o sistema deve aceitar o upload
    E a idempotência deve considerar apenas envios anteriores com status de sucesso
