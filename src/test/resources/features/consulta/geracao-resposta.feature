# language: pt
@pendente
Funcionalidade: Geração de resposta fundamentada
  Cobre RF26 (composição do prompt contextual final e resposta fundamentada
  nos documentos e relações mapeadas).

  @RF26
  Cenário: Composição do prompt contextual e resposta fundamentada
    Dado que a recuperação híbrida retornou chunks relevantes para a pergunta "como funciona a política de férias?"
    Quando o sistema compor o prompt contextual final e enviá-lo à LLM
    Então o prompt deve incluir o conteúdo dos chunks pais correspondentes aos chunks recuperados
    E a resposta entregue deve ser fundamentada nos documentos e relações mapeadas

  @RF26
  Cenário: Resposta cita os documentos de origem
    Dado que a resposta foi gerada a partir de chunks dos documentos "politica-rh.pdf" e "manual-gestor.pdf"
    Quando a resposta for entregue ao usuário
    Então as fontes "politica-rh.pdf" e "manual-gestor.pdf" devem ser citadas na resposta

  @RF26
  Cenário: Pergunta sem contexto relevante não gera resposta inventada
    Dado que a recuperação híbrida não retornou nenhum chunk relevante para a pergunta "qual a previsão do tempo?"
    Quando o sistema processar a etapa de geração
    Então a resposta deve informar que não há base nos documentos ingeridos para responder
    E nenhuma fonte deve ser citada
