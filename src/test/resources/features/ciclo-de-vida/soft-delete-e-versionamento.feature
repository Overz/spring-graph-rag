# language: pt
Funcionalidade: Exclusão lógica e substituição de versão
  Cobre RF10 (Soft Delete com isolamento de grafo e semântica de substituição
  de versão).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF10 @SoftDelete
  Cenário: Exclusão lógica mantendo integridade do grafo
    Dado que o "Documento_A" do "dev_user" gerou a entidade "Spring Boot" no Neo4j
    E o "Documento_B" de outro usuário na mesma empresa também se conecta a "Spring Boot"
    Quando o "dev_user" comandar a exclusão do "Documento_A"
    Então o sistema deve alterar a flag "isActive" para "false" no "Documento_A" e em seus "Chunks" no Neo4j
    E deve inativar os vetores correspondentes no OpenSearch
    E a entidade "Spring Boot" deve ser preservada no grafo, pois está ligada ao "Documento_B"

  @RF10
  Cenário: Vetores inativados não aparecem mais nos resultados de busca
    Dado que o "Documento_A" do "dev_user" foi excluído logicamente
    Quando qualquer busca vetorial ou de grafo for executada no tenant "acme_inc"
    Então nenhum chunk do "Documento_A" deve aparecer nos resultados
    E os filtros de recuperação devem considerar apenas nós e vetores com "isActive = true"

  @RF10
  Cenário: Substituição de arquivo por nova versão reprocessa o pipeline completo
    Dado que o arquivo "contrato.pdf" com identificador "doc-456" e versão 1 está com status "COMPLETED"
    Quando o usuário substituir o arquivo "contrato.pdf" por uma nova versão
    Então a versão anterior deve seguir o fluxo de Soft Delete
    E a nova versão deve ser registrada como versão 2
    E o pipeline completo deve ser reexecutado para o novo conteúdo

  @RF10
  Cenário: Exclusão de documento cujas entidades não são referenciadas por outros documentos
    Dado que o "Documento_X" do "dev_user" é o único documento conectado à entidade "Framework Interno" no Neo4j
    Quando o "dev_user" comandar a exclusão do "Documento_X"
    Então o documento e seus chunks devem ser marcados com "isActive = false"
    E a entidade "Framework Interno" deve permanecer no grafo até a próxima execução do Garbage Collection

  @RF10 @RF30
  Cenário: Usuário não pode excluir documento de outro usuário
    Dado que o "Documento_B" pertence ao usuário "outra_pessoa" do tenant "acme_inc"
    Quando o "dev_user" comandar a exclusão do "Documento_B"
    Então a operação deve ser negada por falta de permissão
    E o documento deve permanecer com "isActive = true"

  @RF10
  Cenário: Substituição de versão aceita reverter para conteúdo de uma versão anterior do mesmo documento
    Dado que o arquivo "ata-reuniao.pdf" com conteúdo "correto" foi enviado com sucesso
    E o usuário substituir a versão atual do "ata-reuniao.pdf" por conteúdo "errado"
    Quando o usuário substituir a versão atual do "ata-reuniao.pdf" por conteúdo "correto"
    Então a substituição deve ser aceita

  @RF10
  Cenário: Substituição de versão aceita conteúdo já usado em outro documento ativo do mesmo usuário
    Dado que o arquivo "documento-base.pdf" com conteúdo "conteudo-compartilhado" foi enviado com sucesso
    E que o arquivo "outro-documento.pdf" foi enviado com sucesso
    Quando o usuário substituir a versão atual do "outro-documento.pdf" por conteúdo "conteudo-compartilhado"
    Então a substituição deve ser aceita

  @RF10
  Cenário: Substituição de versão ainda rejeita arquivo com malware detectado
    Dado que o arquivo "contrato-atual.pdf" foi enviado com sucesso
    Quando o usuário substituir a versão atual do "contrato-atual.pdf" por um arquivo com assinatura EICAR
    Então a substituição deve ser rejeitada com o motivo "MALWARE_DETECTED"

  @RF10 @RF30
  Cenário: Usuário não pode substituir versão de documento de outro usuário
    Dado que o "Documento_B" pertence ao usuário "outra_pessoa" do tenant "acme_inc"
    Quando o "dev_user" tentar substituir a versão do "Documento_B"
    Então a operação deve ser negada por falta de permissão
    E o documento deve permanecer com "isActive = true"
