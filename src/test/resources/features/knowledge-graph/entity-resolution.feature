# language: pt
@pendente
Funcionalidade: Resolução de entidades antes da persistência
  Cobre RF32 (resolução determinística e probabilística de entidades, com
  histórico de aliases e isolamento estrito por tenant).

  @RF32 @EntityResolution @Grafo
  Cenário: Merge automático de entidade duplicada entre documentos
    Dado que o "Documento_C" gerou a entidade "PostgreSQL" com nome normalizado "postgresql"
    E já existe um nó "Entity" com nome normalizado "postgresql" no mesmo tenantId, criado a partir do "Documento_A"
    Quando o processo de resolução de entidades comparar a nova extração com o nó existente
    Então o sistema deve identificar a correspondência exata por nome normalizado e tipo
    E deve reutilizar o nó "Entity" existente em vez de criar um novo
    E deve conectar o novo "Chunk" do "Documento_C" ao nó "Entity" já existente

  @RF32
  Esquema do Cenário: Normalização determinística de nomes de entidades
    Dado que a extração produziu a entidade "<nome_bruto>" do tipo "<tipo>"
    Quando a normalização determinística for aplicada
    Então o nome normalizado deve ser "<nome_normalizado>"

    Exemplos:
      | nome_bruto | tipo         | nome_normalizado |
      | PostgreSQL | TECHNOLOGY   | postgresql       |
      | POSTGRESQL | TECHNOLOGY   | postgresql       |
      | São Paulo  | LOCATION     | sao paulo        |
      | Petrobrás  | ORGANIZATION | petrobras        |

  @RF32
  Cenário: Merge probabilístico automático acima do limiar de confiança
    Dado que a entidade extraída "K8s" não possui match exato por nome normalizado
    E a similaridade de cosseno entre "K8s" e a entidade existente "Kubernetes" é de 0.92
    Quando a etapa probabilística da resolução de entidades for executada
    Então as entidades devem ser mescladas automaticamente, pois a similaridade excede o limiar de 0.85
    E o nó resultante deve registrar "K8s" na propriedade "aliases"

  @RF32
  Cenário: Similaridade na faixa intermediária vai para fila de revisão manual
    Dado que a similaridade de cosseno entre a entidade extraída "Postgres XL" e a existente "PostgreSQL" é de 0.78
    Quando a etapa probabilística da resolução de entidades for executada
    Então nenhum merge automático deve ocorrer
    E o par de entidades deve ser adicionado à fila de revisão manual

  @RF32
  Cenário: Similaridade baixa resulta em criação de novo nó
    Dado que a entidade extraída "Terraform" não possui match exato nem candidato com similaridade acima de 0.5
    Quando a resolução de entidades for concluída
    Então um novo nó "Entity" deve ser criado para "Terraform"

  @RF32 @RF30
  Cenário: Resolução de entidades nunca mescla nós de tenants diferentes
    Dado que o tenant "empresa_A" possui a entidade "PostgreSQL" com nome normalizado "postgresql"
    E o tenant "empresa_B" extraiu uma nova entidade "PostgreSQL" com o mesmo nome normalizado
    Quando a resolução de entidades for executada para o tenant "empresa_B"
    Então um nó "Entity" independente deve ser criado no escopo do tenant "empresa_B"
    E nenhuma comparação ou merge deve considerar entidades do tenant "empresa_A"
