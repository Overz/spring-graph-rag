# language: pt
@pendente
Funcionalidade: Conformidade com LGPD - direito ao esquecimento
  Cobre RF36 (exclusão definitiva síncrona e verificável vinculada a um
  titular de dados, distinta do Soft Delete do RF10 e do GC do RF11).

  @RF36 @LGPD @DireitoAoEsquecimento
  Cenário: Solicitação de exclusão definitiva de dados de um titular
    Dado que a entidade "Maria Silva" do tipo "PERSON" está conectada a chunks de três documentos distintos no tenant "acme_inc"
    Quando um administrador autorizado registrar uma solicitação de exclusão definitiva para o titular "Maria Silva"
    Então o sistema deve localizar todos os nós, relacionamentos, chunks e vetores associados a essa entidade
    E deve executar a remoção física (Hard Delete) de forma síncrona, sem depender do ciclo do Garbage Collection do RF11
    E deve registrar essa operação no log de auditoria imutável do RF31

  @RF36
  Cenário: Localização de todos os dados associados a um titular
    Dado que a entidade "Maria Silva" do tipo "PERSON" existe no grafo do tenant "acme_inc"
    Quando um administrador autorizado solicitar o mapeamento de dados do titular "Maria Silva"
    Então o sistema deve listar todos os nós, relacionamentos, chunks e vetores associados ao titular
    E deve indicar os documentos de origem de cada ocorrência

  @RF36
  Cenário: Exclusão de titular é síncrona e verificável
    Dado que a exclusão definitiva do titular "Maria Silva" foi executada
    Quando qualquer busca por dados do titular for realizada em seguida
    Então nenhum nó, relacionamento, chunk ou vetor associado ao titular deve existir
    E a verificação deve poder ser emitida como evidência da exclusão

  @RF36 @RF11
  Cenário: Exclusão LGPD não depende do ciclo do Garbage Collection
    Dado que uma solicitação de exclusão definitiva foi registrada para o titular "João Santos"
    Quando a exclusão for executada
    Então a remoção física deve ocorrer imediatamente, de forma síncrona
    E não deve aguardar a próxima execução do job assíncrono de Garbage Collection
