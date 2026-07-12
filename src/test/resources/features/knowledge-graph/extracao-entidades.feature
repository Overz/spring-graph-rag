# language: pt
@pendente
Funcionalidade: Extração híbrida de entidades e relacionamentos
  Cobre RF21 (extração dupla via NER rápido e LLM com ontologia restrita,
  output estruturado e schema versionado).

  @RF21
  Cenário: Extração de entidades triviais com modelo NER rápido
    Dado que o chunk "chunk-01" contém menções a pessoa, local e data
    Quando a extração híbrida processar o chunk
    Então as entidades triviais devem ser extraídas pelo modelo NER, sem chamada à LLM:
      | entidade    | tipo     |
      | Maria Silva | PERSON   |
      | São Paulo   | LOCATION |
      | 12/03/2024  | DATE     |

  @RF21
  Cenário: Extração de relacionamentos complexos via LLM com output estruturado
    Dado que o chunk "chunk-02" descreve que o sistema de faturamento depende do serviço de câmbio
    Quando a extração via LLM processar o chunk
    Então a resposta deve ser obtida via output estruturado, dentro do schema fechado
    E o relacionamento "DEPENDS_ON" deve ser extraído entre as entidades "sistema de faturamento" e "serviço de câmbio"

  @RF21
  Esquema do Cenário: Tipos de entidade permitidos pela ontologia restrita
    Dado que a extração identificou o termo "<termo>" como candidato a entidade
    Quando a resposta da LLM classificar o termo como "<tipo>"
    Então a entidade deve ser aceita, pois o tipo "<tipo>" pertence ao schema fechado

    Exemplos:
      | termo          | tipo         |
      | Ana Souza      | PERSON       |
      | Petrobras      | ORGANIZATION |
      | Pix            | PRODUCT      |
      | Apache Kafka   | TECHNOLOGY   |
      | Rio de Janeiro | LOCATION     |
      | 2025-01-15     | DATE         |
      | escalabilidade | CONCEPT      |

  @RF21
  Cenário: Relacionamento fora do schema é mapeado para RELATED_TO
    Dado que a LLM identificou entre "API Gateway" e "catálogo de serviços" uma relação que não se encaixa nos tipos definidos
    Quando a extração validar o relacionamento contra o schema
    Então o relacionamento deve ser registrado como "RELATED_TO"
    E deve receber uma propriedade descritiva explicando a natureza da relação
    E a extração não deve ser rejeitada

  @RF21 @RF34
  Cenário: Entidade com tipo fora do schema fechado é descartada
    Dado que a resposta da LLM classificou o termo "cupom-99" com o tipo inexistente "COUPON"
    Quando a extração validar a resposta contra o schema fechado de tipos
    Então a entidade "cupom-99" deve ser descartada
    E o processamento dos demais itens da resposta deve continuar

  @RF21
  Cenário: Versão do schema registrada em cada extração
    Dado que o schema de extração vigente é a versão 2
    Quando qualquer entidade ou relacionamento for extraído
    Então o registro deve indicar a versão 2 do schema utilizada
    E uma mudança futura de schema deve prever migração das entidades extraídas em versões anteriores
