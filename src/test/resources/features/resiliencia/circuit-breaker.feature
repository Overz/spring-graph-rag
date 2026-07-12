# language: pt
@pendente
Funcionalidade: Circuit breaker para chamadas a LLM e embedding
  Cobre RF37 (proteção por circuit breaker e timeout, com fallback por etapa,
  distinta do retry do RF29).

  @RF37
  Cenário: Circuito abre após degradação sustentada do provedor
    Dado que as chamadas ao provedor de embedding estão excedendo o timeout configurado de forma sustentada
    Quando a taxa de falhas ultrapassar o limiar do circuit breaker
    Então o circuito deve abrir
    E as chamadas seguintes devem falhar imediatamente, sem aguardar timeout
    E as threads e conexões não devem ficar presas em chamadas penduradas

  @RF37
  Cenário: Fallback da etapa de embedding com circuito aberto
    Dado que o circuito para o provedor de embedding está aberto
    Quando a etapa "EMBEDDING" do documento "doc-950" for disparada
    Então o processamento deve ser enfileirado para nova tentativa posterior
    E o documento não deve ser marcado como "FAILED"

  @RF37
  Cenário: Fallback da geração de resposta com circuito aberto
    Dado que o circuito para o provedor de geração está aberto
    Quando um usuário submeter uma consulta
    Então a resposta deve ser degradada para o resultado da busca vetorial, sem a etapa de geração
    E a requisição do usuário não deve ficar travada aguardando o provedor

  @RF37
  Cenário: Circuito fecha após recuperação do provedor
    Dado que o circuito está no estado meio-aberto
    Quando as chamadas de sondagem ao provedor forem bem-sucedidas
    Então o circuito deve fechar
    E o fluxo normal de chamadas deve ser restabelecido
