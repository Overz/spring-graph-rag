# language: pt
@pendente
Funcionalidade: Isolamento multitenant
  Cobre RF30 (validação estrita de permissões por userId e tenantId em
  upload, consulta, exclusão e reprocessamento).

  @RF30 @RF24 @Seguranca @Multitenant
  Cenário: Isolamento rigoroso de contexto global entre empresas distintas
    Dado que o banco de grafos possui uma comunidade de entidades mapeadas sob o tenantId "empresa_A"
    Quando um agente LLM operando em nome do usuário logado na "empresa_B" disparar uma consulta MCP global
    Então o serviço GraphRAG deve injetar o filtro "tenantId = empresa_B" na query Cypher e na busca vetorial
    E a LLM não deve receber nenhum fragmento de conhecimento ou entidade pertencente à "empresa_A"

  @RF30
  Esquema do Cenário: Operações negadas sem permissão adequada
    Dado que o documento "doc-privado" pertence ao usuário "alice" do tenant "empresa_A"
    Quando o usuário "<usuario>" do tenant "<tenant>" tentar executar a operação "<operacao>" sobre o documento
    Então a operação deve ser negada por falta de permissão
    E um evento de auditoria deve registrar a tentativa

    Exemplos:
      | usuario | tenant    | operacao        |
      | bob     | empresa_B | consulta        |
      | bob     | empresa_B | exclusão        |
      | carol   | empresa_A | exclusão        |
      | carol   | empresa_A | reprocessamento |

  @RF30
  Cenário: Proprietário executa operações sobre o próprio documento
    Dado que o documento "doc-privado" pertence ao usuário "alice" do tenant "empresa_A"
    Quando o usuário "alice" do tenant "empresa_A" tentar executar a operação "consulta" sobre o documento
    Então a operação deve ser autorizada
