# language: pt
@pendente
Funcionalidade: Auditoria imutável de operações
  Cobre RF31 (log imutável de todas as ações de estado: envios, deleções
  lógicas, reprocessamentos e mudanças críticas do ciclo de vida).

  @RF31
  Esquema do Cenário: Ações de estado geram registros imutáveis de auditoria
    Dado que o usuário "dev_user" executou a ação de "<acao>" sobre um documento
    Quando a ação for concluída
    Então uma entrada imutável deve ser adicionada ao log de auditoria
    E a entrada deve conter o autor, a ação, o documento afetado, o timestamp e o correlationId

    Exemplos:
      | acao            |
      | envio           |
      | deleção lógica  |
      | reprocessamento |
      | exclusão LGPD   |

  @RF31
  Cenário: Log de auditoria não pode ser alterado nem apagado
    Dado que existe uma entrada de auditoria registrada para o envio do documento "doc-100"
    Quando qualquer tentativa de alteração ou remoção dessa entrada for executada
    Então a operação deve ser rejeitada
    E a tentativa de violação deve gerar um novo registro de auditoria
