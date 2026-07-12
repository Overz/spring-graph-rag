# language: pt
@pendente
Funcionalidade: Chunking hierárquico de documentos Markdown
  Cobre RF18 (estrutura dos chunks e estratégia de chunking hierárquico
  pai-filho, com fallback de tamanho fixo para conteúdo sem estrutura).

  @RF18
  Cenário: Estrutura mínima de cada chunk gerado
    Dado que o documento Markdown "politica.md" está pronto para a etapa "CHUNKING"
    Quando o documento for dividido em fragmentos
    Então cada chunk deve possuir identificador, conteúdo textual, posição e o identificador do documento de origem

  @RF18
  Cenário: Chunking hierárquico com chunks pai e filho
    Dado que o documento "arquitetura.md" possui seções bem definidas
    Quando a estratégia de chunking hierárquico for aplicada
    Então cada seção deve gerar um chunk pai com granularidade entre 1500 e 2000 tokens
    E cada chunk pai deve ser subdividido em chunks filhos com granularidade entre 300 e 500 tokens
    E a relação pai-filho deve ser persistida
    E apenas os chunks filhos devem ser encaminhados para embedding e indexação

  @RF18
  Cenário: Chunk pai compõe o contexto expandido da geração
    Dado que o chunk filho "chunk-f-01" pertence ao chunk pai "chunk-p-01"
    Quando o chunk filho "chunk-f-01" for recuperado em uma consulta
    Então o conteúdo do chunk pai "chunk-p-01" deve estar disponível para compor o contexto final enviado à LLM

  @RF18
  Cenário: Chunking de tamanho fixo com overlap para conteúdo sem estrutura semântica
    Dado que o documento "vendas.csv" não possui estrutura semântica de seções
    Quando a etapa "CHUNKING" processar o documento
    Então os chunks devem ser gerados com tamanho fixo
    E deve haver sobreposição de 10 a 15 por cento entre chunks consecutivos
