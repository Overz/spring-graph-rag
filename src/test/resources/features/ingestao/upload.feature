# language: pt
Funcionalidade: Upload e armazenamento de arquivos para ingestão no GraphRAG
  Cobre RF01 (upload de arquivo), RF03 (limites de tamanho e cotas),
  RF05 (armazenamento do original em Object Storage segregado por tenant e usuário)
  e RF06 (metadados na base relacional).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF01
  Cenário: Upload bem-sucedido de arquivo suportado
    Dado um arquivo "relatorio.pdf" do tipo "application/pdf" com tamanho de "2MB"
    Quando o usuário enviar o arquivo para processamento
    Então o sistema deve aceitar o upload
    E deve responder com o identificador único do documento
    E o status inicial do documento deve ser "RECEIVED"

  @RF05
  Cenário: Armazenamento do arquivo original segregado por tenant e usuário
    Dado um arquivo "contrato.pdf" do tipo "application/pdf" com tamanho de "1MB"
    Quando o usuário enviar o arquivo para processamento
    E o upload for aceito com o identificador "doc-123"
    Então o arquivo original deve ser salvo no Object Storage no caminho "/acme_inc/dev_user/raw/doc-123/contrato.pdf"
    E o conteúdo armazenado deve ser idêntico byte a byte ao arquivo enviado

  @RF06
  Cenário: Persistência dos metadados do arquivo na base relacional
    Dado um arquivo "notas.md" do tipo "text/markdown" com tamanho de "10KB"
    Quando o usuário enviar o arquivo para processamento
    Então os seguintes metadados devem ser persistidos na base relacional:
      | campo        | valor                                    |
      | ownerId      | dev_user                                 |
      | tenantId     | acme_inc                                 |
      | nomeOriginal | notas.md                                 |
      | extensao     | md                                       |
      | tamanho      | 10240                                    |
      | hash         | SHA-256 do conteúdo                      |
      | localizacao  | /acme_inc/dev_user/raw/{fileId}/notas.md |
      | versao       | 1                                        |
      | status       | RECEIVED                                 |
    E a data de envio deve ser registrada
    E um identificador único deve ser gerado para o documento

  @RF03
  Esquema do Cenário: Aplicação do limite de tamanho por arquivo individual
    Dado um arquivo "<arquivo>" do tipo "application/pdf" com tamanho de "<tamanho>"
    Quando o usuário enviar o arquivo para processamento
    Então o resultado do upload deve ser "<resultado>"

    Exemplos:
      | arquivo       | tamanho | resultado |
      | pequeno.pdf   | 512KB   | aceito    |
      | no-limite.pdf | 5MB     | aceito    |
      | grande.pdf    | 6MB     | rejeitado |
      | enorme.pdf    | 50MB    | rejeitado |

  @RF03
  Cenário: Rejeição de arquivo acima do limite informa o motivo
    Dado um arquivo "manual-escaneado.pdf" do tipo "application/pdf" com tamanho de "12MB"
    Quando o usuário enviar o arquivo para processamento
    Então o upload deve ser rejeitado com o motivo "Tamanho máximo de 5MB excedido"
    E o arquivo não deve ser salvo no Object Storage em "/raw"
    E a resposta deve orientar o usuário a pré-dividir documentos grandes

  @RF03
  Cenário: Rejeição de upload quando a cota de armazenamento do tenant está esgotada
    Dado que o tenant "acme_inc" possui cota de armazenamento total de "1GB"
    E o tenant "acme_inc" já ocupa "1023MB" de armazenamento com arquivos ativos
    E um arquivo "extra.pdf" do tipo "application/pdf" com tamanho de "2MB"
    Quando o usuário enviar o arquivo para processamento
    Então o upload deve ser rejeitado com o motivo "Cota de armazenamento do tenant excedida"
    E nenhum novo evento do ciclo de vida deve ser publicado
