# Requisitos Funcionais e Arquiteturais - Plataforma GraphRAG (Documento Consolidado)

> Este documento é a junção de `requisitos.md` (RF01-RF31 + BDD original) com `requisitos-complemento.md` (complementos, revisões e novos requisitos RF32-RF39/RNF01-04 + novos cenários BDD). Os complementos/revisões foram incorporados diretamente na seção do RF original correspondente, marcados como *(complemento)* ou *(revisão)*, seguindo a orientação do próprio adendo. Os itens novos foram anexados nas seções 10-13.

## 1. Gestão de Arquivos e Ingestão

### RF01 - Upload de arquivo
O sistema deve permitir que um usuário envie arquivos para processamento no GraphRAG.

### RF02 - Validação inicial do arquivo
O sistema deve validar: tamanho máximo permitido, extensão do arquivo, tipo MIME, integridade do arquivo, nome do arquivo e duplicidade de envio. Caso alguma validação falhe, o sistema deve rejeitar o arquivo informando o motivo.

**RF02 (complemento) — Verificação de Malware:** além das validações acima, o sistema deve submeter o arquivo a uma varredura antivírus/antimalware (ex: integração com ClamAV, compatível com o setup local e open-source do projeto) antes de liberá-lo para o status `UPLOADED`. PDFs e imagens são vetores conhecidos de exploits embutidos. Uma falha na varredura deve gerar rejeição com motivo específico (`MALWARE_DETECTED`), sem consumir cota de reprocessamento.

### RF03 - Limites de tamanho e volume
- O sistema deve aceitar arquivos individuais de até **5MB**.
- Se o payload acumulado enviado pelo usuário/tenant ultrapassar **10MB**, o sistema deve realizar o recebimento via fila de mensageria para garantir a estabilidade do serviço.

**RF03 (complemento) — Cotas:** definir uma cota por tenant (armazenamento total e/ou número de arquivos ativos), independente do limite de 10MB de payload acumulado em processamento — este último trata de carga transiente, não de volume total armazenado. Reavaliar o limite de 5MB por arquivo individual à luz de PDFs escaneados multipágina, que frequentemente ultrapassam esse teto. Se o limite for mantido, RF15 (OCR) deve orientar o cliente a pré-dividir documentos grandes.

### RF04 - Tipos de arquivos suportados
O sistema deve aceitar inicialmente: PDF, JPG, JPEG, PNG, CSV, JSON, XML, TXT e Markdown.

**RF04 (complemento) — Escopo de Ingestão:** avaliar a inclusão de DOCX, XLSX e PPTX na lista de tipos suportados — formatos comuns em contexto corporativo, hoje fora do escopo.

### RF05 - Armazenamento do arquivo original
O sistema deve armazenar o arquivo original em Object Storage segregado por tenant e usuário.
Exemplo: `/{tenantId}/{userId}/raw/{fileId}/{filename}`

### RF06 - Metadados do arquivo
O sistema deve armazenar na base de dados relacional: identificador, usuário proprietário (`ownerId`), empresa (`tenantId`), nome original, extensão, tamanho, hash (SHA-256), data de envio, localização no storage, versão e status.

### RF07 - Idempotência por Hash de Arquivo
O sistema deve gerar o hash SHA-256 no momento do upload. Se o mesmo hash já existir para o usuário/tenant (com status de sucesso), o sistema deve rejeitar o reprocessamento para evitar duplicação em bases vetoriais e de grafos, a menos que haja um comando explícito de reprocessamento.

---

## 2. Ciclo de Vida e Atualização

### RF08 - Status de processamento
Estados obrigatórios do ciclo de vida:
`RECEIVED` -> `VALIDATING` -> `UPLOADED` -> `QUEUED` -> `EXTRACTING` -> `TRANSFORMING` -> `CHUNKING` -> `EMBEDDING` -> `GRAPH_BUILDING` -> `COMPLETED` | `FAILED`

**RF08 (revisão) — Paralelização de EMBEDDING e GRAPH_BUILDING:** no desenho atual, `EMBEDDING` e `GRAPH_BUILDING` são etapas sequenciais, mas ambas dependem apenas da saída de `CHUNKING` — não há dependência entre si. Propõe-se um modelo fork-join:

```
CHUNKING -> [EMBEDDING || GRAPH_BUILDING] -> COMPLETED | PARTIALLY_COMPLETED | FAILED
```

Implicações:
- O status do documento passa a agregar dois sub-estados independentes (ex: `embeddingStatus`, `graphStatus`), com o status geral derivado dos dois.
- Decisão necessária: se uma das branches falhar definitivamente, o documento pode ficar `PARTIALLY_COMPLETED` (ex: busca vetorial funcional, mas sem enriquecimento de grafo) em vez de `FAILED` puro — um documento só com embeddings ainda tem valor para RF25. Recomenda-se permitir esse estado intermediário em vez de bloquear tudo por falha em um único ramo.

### RF09 - Consulta e Histórico
O sistema deve permitir consultar o status atual e registrar o histórico completo das etapas executadas por arquivo.

**RF09 (revisão) — Visibilidade compartilhada no tenant:** a consulta de status e histórico não é restrita ao dono do documento — qualquer usuário autenticado do mesmo tenant pode consultar status/histórico de qualquer documento do tenant (visão compartilhada, RF40). Isolamento entre tenants continua absoluto (RF30): documento de outro tenant responde como inexistente. Exclusão/substituição de versão continuam restritas ao dono (RF10).

### RF10 - Estratégia de Atualização e Exclusão (Soft Delete)
- O sistema deve permitir substituir ou excluir um arquivo no Object Storage para o usuário em questão.
- **Isolamento de Grafo:** A exclusão/substituição de um arquivo marca o nó `Document` e seus `Chunks` associados com uma flag `isActive = false` no banco de grafos (Neo4j) e inativa/remove seus embeddings no VectorDB (OpenSearch).
- Entidades (`Entity`) e Relacionamentos conectados a esses chunks permanecem intactos se estiverem sendo referenciados por outros documentos ativos.

**RF10 (complemento) — Semântica de Substituição de Versão:** ao substituir um arquivo por nova versão, o sistema deve reprocessar o pipeline completo para o novo conteúdo (repetindo RF12 em diante), com a versão anterior seguindo o fluxo de Soft Delete já descrito. Diffing incremental (reprocessar só os trechos alterados) fica registrado como otimização futura — o custo de implementação não se justifica antes de o pipeline completo estar validado.

### RF11 - Expurgamento de Entidades Órfãs (Garbage Collection)
Um processo em background deve varrer o banco de grafos periodicamente para remover fisicamente nós de entidades e relacionamentos que não possuem mais conexões com nenhum chunk ativo (`isActive = true`).

### RF40 - Listagem de Documentos
O sistema deve permitir listar os documentos de um tenant de forma paginada — visão compartilhada entre todos os usuários autenticados do tenant (mesma regra de RF09 revisado), não restrita ao dono de cada documento.

- Por padrão, a listagem traz apenas documentos ativos (`isActive = true`); um parâmetro opcional permite incluir também os excluídos logicamente, com o padrão desligado (`includeInactive = false`).
- Nomes de arquivo duplicados entre documentos distintos são permitidos e não são deduplicados na listagem.
- Cada item traz, no mínimo: identificador, nome do arquivo, status atual, quem enviou (`ownerId`), versão, data de criação e data da última atualização.
- Isolamento entre tenants é absoluto (RF30): a listagem nunca inclui documentos de outro tenant.

---

## 3. Processamento Assíncrono e Resiliência

### RF12 - Event-Driven Architecture
Após a persistência inicial, o sistema deve enviar eventos internos (utilizando abstrações de módulos como Spring Modulith) para iniciar o processamento. O design deve prever fácil transição para mensageria externa (ex: Kafka) conforme a volumetria exigir.

### RF13 - Fila de processamento
O sistema deve garantir controle de carga, isolamento de falhas, idempotência no consumo de eventos e políticas de retry para resiliência.

---

## 4. Extração e Transformação

### RF14 - Identificação e Delegação
O sistema deve identificar o formato do arquivo recebido e delegar ao processador adequado (interfaces segregadas por tipo MIME).

### RF15 - Extração e OCR
O sistema deve extrair o conteúdo textual nativo e utilizar OCR integrado para documentos sem camada textual (ex: JPG, PNG, PDFs escaneados).

### RF16 - Normalização para Markdown
O sistema deve transformar todo o conteúdo textual e estrutural extraído para um formato Markdown padronizado.

### RF17 - Armazenamento Transformado
O sistema deve salvar o arquivo Markdown gerado no Object Storage e atualizar o status na base relacional.
Exemplo: `/{tenantId}/{userId}/transformed/{fileId}/{version}.md`

---

## 5. Pipeline RAG (Vetores)

### RF18 - Chunking
O sistema deve dividir os documentos Markdown em fragmentos menores. Cada chunk deve possuir: identificador, conteúdo textual, posição e ID do documento de origem.

**RF18 (revisão) — Estratégia de Chunking Hierárquico:** este RF deve detalhar a estratégia, não só a estrutura de dados do chunk:
- **Chunk pai (contexto expandido):** granularidade maior (ex: seção do documento, 1500–2000 tokens), usado para compor o contexto final enviado à LLM em RF26.
- **Chunk filho (embedding preciso):** granularidade menor (ex: 300–500 tokens), é o que recebe embedding (RF19) e é indexado no OpenSearch.
- A relação pai-filho deve ser persistida (como relacionamento no Neo4j ou referência na base relacional).
- Isso resolve a questão arquitetural original do projeto: hierarquia pai-filho e grafo de entidades não são abordagens concorrentes, são complementares — a hierarquia dá profundidade de contexto textual, o grafo dá amplitude de contexto relacional entre documentos diferentes.
- Para conteúdo sem estrutura semântica clara (ex: CSV, JSON), definir chunking de tamanho fixo com overlap de 10–15%.

### RF19 - Embeddings
O sistema deve enviar um evento para chamar a LLM (ou modelo de embedding específico) e gerar as representações vetoriais dos chunks.

### RF20 - Armazenamento Vetorial
O sistema deve salvar os embeddings em um datasource vetorial (OpenSearch), mantendo os metadados necessários para filtros de busca.

---

## 6. Knowledge Graph (Grafos)

### RF21 - Extração Híbrida de Entidades e Relacionamentos
O sistema deve construir a base de conhecimento através de uma abordagem dupla:
1. **Modelos Rápidos (NER):** Extração de entidades triviais (pessoas, locais, datas) para otimizar custos.
2. **LLM via Prompt:** Extração de relacionamentos complexos, regras de negócio e contextos implícitos contidos nos chunks.

**RF21 (revisão) — Ontologia Restrita para Extração:** a extração via LLM deve ser restrita a um schema fechado de tipos, não texto livre:
- **Tipos de entidade:** ex. `PERSON`, `ORGANIZATION`, `PRODUCT`, `TECHNOLOGY`, `LOCATION`, `DATE`, `CONCEPT`.
- **Tipos de relacionamento:** ex. `USES`, `DEPENDS_ON`, `PART_OF`, `AUTHORED_BY`, `MENTIONS`, com um tipo de escape `RELATED_TO` + propriedade descritiva para casos que não se encaixam, em vez de rejeitar a extração.
- Usar output estruturado (structured output / tool calling do Spring AI) para forçar a resposta da LLM dentro desse schema, em vez de pós-processar texto livre.
- O schema deve ser versionado; mudanças nele implicam estratégia de migração para entidades já extraídas na versão anterior.

### RF22 - Construção do Grafo
O sistema deve criar nós (`Document`, `Chunk`, `Entity`) e relacionamentos no banco de grafos (Neo4j).

### RF23 - Integração Vetor-Grafo para Model Context Protocol (MCP)
O nó do tipo `Chunk` no Neo4j deve obrigatoriamente armazenar a propriedade `openSearchId` correspondente à sua representação no VectorDB. Isso fornecerá a base para as ferramentas MCP cruzarem contexto topológico com busca semântica profunda.

### RF24 - Escopo Global vs. Local (Comunidades)
O modelo do grafo deve suportar agrupamentos. Insights globais e análises de comunidades (ex: agrupamento de temas da empresa inteira) devem ser orientados pelo `tenantId`, enquanto consultas restritas devem validar rigorosamente o `ownerId`.

---

## 7. Consulta GraphRAG

### RF25 - Recuperação de Contexto e Disponibilização MCP
O sistema deve possuir endpoints ou ferramentas disponibilizadas via protocolo MCP que combinem:
- Busca em grafo (Cypher queries para descobrir relações).
- Busca vetorial (para trazer a exatidão semântica a partir dos IDs descobertos no grafo).
- Filtros de metadados (`ownerId`, `tenantId`, `isActive = true`).

**RF25 (revisão) — Estratégia de Fusão na Recuperação Híbrida:** definir explicitamente como os resultados de grafo e vetor são combinados:
- Adotar Reciprocal Rank Fusion (RRF): `score(d) = Σ 1 / (k + rank_i(d))` somado sobre cada lista de ranking `i`, com `k = 60` como ponto de partida padrão da literatura.
- Fluxo: (1) busca vetorial top-N por similaridade; (2) identificação de entidades na query (NER) e travessia do grafo a partir delas, limitada a 1–2 hops para evitar explosão de contexto; (3) fusão via RRF; (4) aplicação dos filtros de metadados já previstos em RF25.
- Rodar as duas buscas sempre em paralelo, deixando a fusão ponderar naturalmente, é mais simples de operar do que um gatilho condicional para decidir quando acionar o grafo.

### RF26 - Geração de Resposta
O sistema deve compor o prompt contextual final, enviá-lo à LLM e entregar a resposta fundamentada nos documentos e relações mapeadas.

---

## 8. Tratamento de Erros

### RF27 - Falha por Etapa
Cada fase deve possuir tratamento isolado de falhas (ex: `EXTRACTION_FAILED`, `EMBEDDING_FAILED`). O processamento deve permitir recuperação parcial, continuando da última etapa concluída com sucesso.

### RF28 - Observabilidade
Todo erro deve registrar: etapa, código, mensagem, timestamp, tentativa e payload de diagnóstico estruturado.

**RF28 (complemento) — Rastreabilidade Ponta a Ponta:** deve ser gerado um `correlationId` único no momento do upload (RF01), propagado por todos os eventos internos e logs de todas as etapas (RF08). É isso que torna viável depurar uma falha em um pipeline de 8 estágios assíncronos. Recomenda-se instrumentação via Micrometer Tracing (Spring Boot 4), além de métricas por etapa: latência, profundidade de fila, taxa de retry e volume em DLQ.

### RF29 - Retry e Dead Letter Queue (DLQ)
Falhas temporárias (ex: Rate Limit da LLM) devem gerar retentativas automáticas. Falhas definitivas (ex: arquivo corrompido) devem ser encaminhadas para uma DLQ, permitindo tratamento e reprocessamento manual por usuários autorizados.

---

## 9. Segurança e Auditoria

### RF30 - Isolamento Multitenant
Validação estrita de permissões (AuthZ) baseadas no `userId` e `tenantId` para operações de upload, consulta, exclusão e reprocessamento.

### RF31 - Auditoria de Operações
Deve ser mantido um log imutável de todas as ações de estado: envios, deleções lógicas, reprocessamentos e mudanças críticas no ciclo de vida.

---

## 10. Qualidade e Integridade do Grafo

### RF32 - Resolução de Entidades (Entity Resolution)
O sistema deve implementar um processo de resolução de entidades antes de persistir novos nós `Entity`, evitando duplicidade semântica (ex: "Spring Boot" extraído de dois documentos distintos deve resultar em um único nó).

- **Etapa 1 (determinística):** normalização de texto (case-folding, remoção de acentos/espaços) seguida de match exato por nome normalizado + tipo, dentro do mesmo `tenantId`.
- **Etapa 2 (probabilística):** para candidatos sem match exato, comparação por similaridade de embedding do nome + contexto local, com merge automático acima de um limiar de confiança (ex: similaridade de cosseno > 0.85) e fila de revisão manual para a faixa intermediária.
- Nós mesclados devem manter histórico de aliases (propriedade `aliases` no nó `Entity`) para rastreabilidade.
- **Restrição crítica:** a resolução de entidades nunca deve mesclar nós de `tenantId` diferentes — isso violaria o isolamento multitenant do RF30. O escopo de resolução é sempre por tenant, nunca global entre empresas.

### RF33 - Avaliação de Qualidade de Recuperação
O sistema deve manter um conjunto de consultas de referência ("golden set") com os chunks/entidades esperados como relevantes, permitindo medir precisão e recall da recuperação. Isso permite comparar empiricamente o ganho da abordagem GraphRAG (grafo + vetor) contra uma recuperação puramente vetorial/hierárquica, validando a decisão arquitetural com dados, não só na teoria.

---

## 11. Segurança Avançada e Conformidade

### RF34 - Isolamento contra Conteúdo Malicioso em Prompts (Prompt Injection)
Conteúdo extraído de arquivos de usuários é, por definição, não confiável e não deve alterar o comportamento da LLM em nenhuma etapa (RF21, RF26):

- Todo conteúdo de documento incluído em um prompt deve ser delimitado explicitamente, com instrução de sistema deixando claro que esse conteúdo é dado a ser analisado, não instrução a ser seguida.
- A saída da extração (RF21) deve ser validada contra o schema fechado de tipos; qualquer resposta fora do schema é descartada, o que mitiga parcialmente a injeção de entidades falsas.
- Chunks com padrões suspeitos de injeção devem ser sinalizados para revisão, sem bloquear o pipeline — detecção perfeita não é uma garantia realista, então o requisito é sinalização, não prevenção absoluta.
- Referência: OWASP Top 10 for LLM Applications, categoria LLM01 (Prompt Injection).

### RF35 - Autenticação e Criptografia
- Autenticação de usuários e das chamadas às ferramentas MCP via OAuth2/OIDC (Spring Security), com tokens JWT.
- Criptografia em trânsito (TLS) para toda comunicação externa e entre serviços internos.
- Criptografia em repouso para Object Storage, PostgreSQL, Neo4j e OpenSearch.

### RF36 - Conformidade com LGPD (Direito ao Esquecimento)
Como entidades do tipo `PERSON` podem ser extraídas para o grafo (RF21), o sistema deve prever um fluxo de exclusão definitiva vinculado a um titular de dados, distinto do Soft Delete do RF10:

- Deve ser possível localizar todos os nós, relacionamentos, chunks e vetores associados a um titular de dados específico.
- A exclusão motivada por solicitação de titular deve ser síncrona e verificável (Hard Delete imediato) — diferente do Garbage Collection do RF11, que é assíncrono e motivado por orfandade técnica, não por uma solicitação legal.

---

## 12. Confiabilidade entre Componentes Distribuídos

### RF37 - Circuit Breaker para Chamadas a LLM/Embedding
Chamadas a provedores de LLM/embedding (RF19, RF21, RF26) devem ser protegidas por circuit breaker e timeout (ex: Resilience4j), mecanismo distinto do retry do RF29:

- Retry (RF29) trata falhas transitórias pontuais; circuit breaker trata degradação sustentada do provedor, evitando esgotamento de threads/conexões por chamadas penduradas.
- Definir fallback por etapa quando o circuito abrir: para RF19, enfileirar para nova tentativa posterior; para RF26, degradar para resposta baseada só em busca vetorial (sem chamada de geração) em vez de travar a requisição do usuário.

### RF38 - Reconciliação entre Neo4j e OpenSearch
Um processo periódico (mesma cadência conceitual do GC do RF11) deve verificar a integridade referencial entre as duas bases: todo nó `Chunk` ativo deve ter um `openSearchId` correspondente existente no OpenSearch, e todo vetor no OpenSearch deve ter um nó `Chunk` correspondente ativo no Neo4j.

- Divergências detectadas devem ser registradas e, quando possível, autocorrigidas (remoção de vetor órfão, ou sinalização do chunk para reindexação).
- Como mitigação preventiva, avaliar padrão outbox nas escritas que afetam as duas bases simultaneamente, reduzindo a necessidade de reconciliação corretiva.

### RF39 - Isolamento de Carga entre Tenants (Fair Queueing)
A fila de processamento (RF13) deve prevenir que um tenant com alto volume de envios monopolize a capacidade de processamento e atrase outros tenants ("noisy neighbor"). Recomenda-se fila particionada por tenant com limite de concorrência por partição — viável de forma mais completa a partir da migração para mensageria externa já prevista no RF12 (ex: partições do Kafka por `tenantId`). Enquanto isso, um limite de concorrência por tenant a nível de listener já mitiga o pior caso.

---

## 13. Requisitos Não Funcionais (RNF)

### RNF01 - Latência de Consulta
A latência de resposta de RF25/RF26 (excluindo o tempo de geração da própria LLM, que varia por provedor) deve ter meta de p95 definida antes da fase de testes de carga. Sugestão de partida: p95 < 3s para a etapa de recuperação (grafo + vetor + fusão).

### RNF02 - Throughput de Ingestão
O pipeline deve sustentar um volume mínimo de arquivos processados por minuto sem degradar o tempo de resposta das consultas em andamento (RF25) — valor a definir conforme a carga esperada do ambiente de destino.

### RNF03 - Backup e Recuperação de Desastres
Neo4j e OpenSearch são bases stateful e centrais — dados perdidos nelas não são reconstruíveis a partir de outra fonte sem reprocessar todo o histórico de arquivos. Deve haver rotina de backup periódico com RPO (Recovery Point Objective) e RTO (Recovery Time Objective) definidos para ambas as bases.

### RNF04 - Disponibilidade
Definir se o caminho de consulta (RF25/RF26) precisa de disponibilidade maior que o caminho de ingestão. Em geral, indisponibilidade temporária de ingestão é mais tolerável para o usuário do que indisponibilidade de consulta.

*Os valores numéricos acima são pontos de partida para discussão, não requisitos fechados — dependem do uso real pretendido para o projeto.*

---

# BDD - Cenários Principais (Behavior-Driven Development)

```gherkin
Funcionalidade: Ingestão, Resiliência e barramento MCP no GraphRAG

  @Idempotencia
  Cenário: Tentativa de upload de arquivo idêntico já processado
    Dado que o usuário "dev_user" do tenant "acme_inc" já enviou o arquivo "arquitetura.pdf" com sucesso
    E o hash SHA-256 computado foi "8f434346648f5b96df89ec301c3b7a5a"
    Quando o usuário submeter o mesmo arquivo "arquitetura.pdf"
    Então o sistema deve interceptar a requisição na validação inicial
    E deve rejeitar a operação para evitar duplicação de entidades e vetores
    E nenhum novo evento do ciclo de vida deve ser publicado

  @SoftDelete
  Cenário: Exclusão lógica mantendo integridade do grafo
    Dado que o "Documento_A" do "dev_user" gerou a entidade "Spring Boot" no Neo4j
    E o "Documento_B" de outro usuário na mesma empresa também se conecta a "Spring Boot"
    Quando o "dev_user" comandar a exclusão do "Documento_A"
    Então o sistema deve alterar a flag "isActive" para "false" no "Documento_A" e em seus "Chunks" no Neo4j
    E deve inativar os vetores correspondentes no OpenSearch
    E a entidade "Spring Boot" deve ser preservada no grafo, pois está ligada ao "Documento_B"

  @MCP @ContextRetrieve
  Cenário: Busca unificada via Model Context Protocol
    Dado que um agente LLM aciona a ferramenta MCP de busca por grafos para o termo "Microsserviços"
    Quando a ferramenta consultar o Neo4j validando a flag de nós ativos
    Então ela deve resgatar as propriedades "openSearchId" dos nós "Chunk" associados a essa entidade
    E deve realizar uma busca por IDs no OpenSearch para extrair o texto original
    E deve compor o retorno unificado (topologia do grafo + blocos de texto) para a LLM

  @Validacao @Limites
  Cenário: Rejeição de arquivo com formato não suportado
    Dado que o usuário "dev_user" tenta realizar o upload de um arquivo chamado "treinamento.mp4"
    Quando o sistema executar as validações iniciais de ingestão
    Então a requisição deve ser rejeitada com um erro de "Tipo MIME não suportado"
    E o arquivo não deve ser salvo no Object Storage em "/raw"

  @ControleDeCarga @Mensageria
  Cenário: Transição automática para fila ao exceder limite de processamento acumulado
    Dado que a empresa "acme_inc" já possui múltiplos arquivos em processamento totalizando 9MB
    Quando um usuário dessa empresa realizar o upload de um novo arquivo PDF de 3MB
    Então o sistema deve validar que o payload acumulado excederá o teto de 10MB
    E o sistema deve aceitar o upload com status "QUEUED"
    E a etapa de extração deve ser delegada estritamente para a fila de mensageria assíncrona, evitando Out Of Memory (OOM)

  @Resiliencia @RecuperacaoParcial
  Cenário: Falha na API de Embedding e recuperação parcial do estado
    Dado que o processamento do "relatorio.md" atingiu com sucesso o status "CHUNKING"
    E o sistema disparou o evento interno para iniciar a etapa de "EMBEDDING"
    Quando a provedora de LLM/Embedding retornar um erro de "Rate Limit" (HTTP 429)
    Então o sistema deve registrar a falha no histórico com o status "EMBEDDING_FAILED"
    E o mecanismo de retry deve ser acionado
    E, na próxima tentativa, o sistema deve retomar a operação a partir do "EMBEDDING", aproveitando os chunks já salvos no banco

  @DLQ @TratamentoDeErros
  Cenário: Falha definitiva encaminhada para Dead Letter Queue
    Dado que o processador falhou ao tentar extrair o texto de um "documento.pdf" corrompido
    E o sistema já esgotou o número máximo de tentativas de reprocessamento (retry)
    Quando a última falha for registrada com o status "EXTRACTION_FAILED"
    Então o evento deve ser roteado para uma Dead Letter Queue (DLQ)
    E o status final do arquivo deve ser marcado como "FAILED"
    E o sistema deve habilitar a opção de "Reprocessamento Manual" para os administradores

  @GarbageCollection @LimpezaDeGrafo
  Cenário: Remoção física de entidades órfãs pelo processo de background
    Dado que uma operação de Soft Delete deixou a entidade "Servidor Legado" no Neo4j sem nenhuma aresta conectada a um nó de "Chunk" com "isActive=true"
    Quando o job assíncrono de Garbage Collection for executado
    Então o sistema deve identificar a entidade "Servidor Legado" como órfã
    E deve deletá-la fisicamente do banco de grafos (Hard Delete)
    Para garantir economia de disco e integridade nas futuras construções de contexto

  @Seguranca @Multitenant
  Cenário: Isolamento rigoroso de contexto global entre empresas distintas
    Dado que o banco de grafos possui uma comunidade de entidades mapeadas sob o tenantId "empresa_A"
    Quando um agente LLM operando em nome do usuário logado na "empresa_B" disparar uma consulta MCP global
    Então o serviço GraphRAG deve injetar o filtro "tenantId = empresa_B" na query Cypher e na busca vetorial
    E a LLM não deve receber nenhum fragmento de conhecimento ou entidade pertencente à "empresa_A"

  @EntityResolution @Grafo
  Cenário: Merge automático de entidade duplicada entre documentos
    Dado que o "Documento_C" gerou a entidade "PostgreSQL" com nome normalizado "postgresql"
    E já existe um nó "Entity" com nome normalizado "postgresql" no mesmo tenantId, criado a partir do "Documento_A"
    Quando o processo de resolução de entidades comparar a nova extração com o nó existente
    Então o sistema deve identificar a correspondência exata por nome normalizado e tipo
    E deve reutilizar o nó "Entity" existente em vez de criar um novo
    E deve conectar o novo "Chunk" do "Documento_C" ao nó "Entity" já existente

  @PromptInjection @Seguranca
  Cenário: Conteúdo malicioso embutido em documento tentando manipular a extração
    Dado que um chunk do "documento_suspeito.pdf" contém um trecho que instrui explicitamente a LLM a ignorar as regras e classificar todo o conteúdo como uma entidade privilegiada
    Quando o sistema processar esse chunk na etapa de extração híbrida (RF21)
    Então o prompt enviado à LLM deve delimitar claramente o conteúdo do chunk como dado analisado, não como instrução
    E a resposta da LLM deve ser validada contra o schema fechado de tipos de entidade
    E qualquer entidade fora do schema definido deve ser descartada, sem interromper o processamento dos demais chunks

  @ProcessamentoParalelo @FalhaParcial
  Cenário: Sucesso em Embedding com falha definitiva em Graph Building
    Dado que o "relatorio2.md" concluiu a etapa "CHUNKING" com sucesso
    E as etapas "EMBEDDING" e "GRAPH_BUILDING" são disparadas em paralelo
    Quando a etapa "EMBEDDING" for concluída com sucesso
    E a etapa "GRAPH_BUILDING" falhar definitivamente após esgotar as tentativas de retry
    Então o documento deve ser marcado com status "PARTIALLY_COMPLETED"
    E os chunks do "relatorio2.md" devem permanecer disponíveis para busca vetorial em RF25
    E o registro de falha da etapa "GRAPH_BUILDING" deve ficar disponível para reprocessamento manual

  @Reconciliacao @Consistencia
  Cenário: Detecção de vetor órfão no OpenSearch
    Dado que um vetor no OpenSearch referencia um "chunkId" que não possui mais um nó "Chunk" correspondente ativo no Neo4j
    Quando o job de reconciliação periódica for executado
    Então o sistema deve identificar essa divergência
    E deve registrar a inconsistência para auditoria
    E deve remover o vetor órfão do OpenSearch para preservar a integridade da base vetorial

  @LGPD @DireitoAoEsquecimento
  Cenário: Solicitação de exclusão definitiva de dados de um titular
    Dado que a entidade "Maria Silva" do tipo "PERSON" está conectada a chunks de três documentos distintos no tenant "acme_inc"
    Quando um administrador autorizado registrar uma solicitação de exclusão definitiva para o titular "Maria Silva"
    Então o sistema deve localizar todos os nós, relacionamentos, chunks e vetores associados a essa entidade
    E deve executar a remoção física (Hard Delete) de forma síncrona, sem depender do ciclo do Garbage Collection do RF11
    E deve registrar essa operação no log de auditoria imutável do RF31
```

---

*Observação: os complementos, revisões e RNFs marcados acima são propostas para discussão e ajuste — em especial os limiares numéricos (similaridade de merge, `k` do RRF, profundidade de hops) e os valores de RNF, que dependem de decisões específicas do projeto.*
