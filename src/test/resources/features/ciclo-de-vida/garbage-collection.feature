# language: pt
Funcionalidade: Expurgamento de entidades órfãs do grafo
  Cobre RF11 (Garbage Collection periódico de nós e relacionamentos sem
  conexão com chunks ativos).

  @RF11 @GarbageCollection @LimpezaDeGrafo
  Cenário: Remoção física de entidades órfãs pelo processo de background
    Para garantir economia de disco e integridade nas futuras construções de contexto.

    Dado que uma operação de Soft Delete deixou a entidade "Servidor Legado" no Neo4j sem nenhuma aresta conectada a um nó de "Chunk" com "isActive=true"
    Quando o job assíncrono de Garbage Collection for executado
    Então o sistema deve identificar a entidade "Servidor Legado" como órfã
    E deve deletá-la fisicamente do banco de grafos (Hard Delete)

  @RF11
  Cenário: Entidade referenciada por chunk ativo não é removida
    Dado que a entidade "Kubernetes" possui pelo menos uma aresta conectada a um nó de "Chunk" com "isActive=true"
    Quando o job assíncrono de Garbage Collection for executado
    Então a entidade "Kubernetes" não deve ser removida do banco de grafos

  @RF11
  Cenário: Relacionamentos órfãos são removidos junto com as entidades órfãs
    Dado que a entidade órfã "Sistema Antigo" possui um relacionamento "DEPENDS_ON" com a entidade órfã "Banco Legado"
    Quando o job assíncrono de Garbage Collection for executado
    Então as entidades "Sistema Antigo" e "Banco Legado" devem ser removidas fisicamente
    E o relacionamento "DEPENDS_ON" entre elas deve ser removido fisicamente
