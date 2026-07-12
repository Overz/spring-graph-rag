# Qualidade e Testes

> Parte do [SDD](../sdd.md). Cobre a estratégia de testes em todas as camadas, o fluxo BDD (critério de aceite do backlog), a infraestrutura de teste (Testcontainers, incluindo Keycloak) e a avaliação de qualidade de recuperação (RF33).

---

## 1. Pirâmide de testes

| Camada | Ferramenta | O que valida | Exemplos concretos |
|---|---|---|---|
| **Unitário** | JUnit | lógica pura, sem contexto Spring | derivação do status geral (função pura — `ingestao.md` §6.2), validadores do upload, fusão RRF, chunker (fronteiras, faixas de token, cabeçalho CSV), normalização de nome do ER |
| **Módulo** | `@ApplicationModuleTest` (Modulith) | um módulo isolado + seus eventos (`Scenario` API: publicar evento → esperar efeito) | `rag`: `DocumentUploadedEvent` → transições até `CHUNKING`; fork publica os dois eventos |
| **Integração** | `@SpringBootTest` + Testcontainers | adaptadores contra serviços reais | mapping do índice OpenSearch, constraints Neo4j, migração Flyway, import do realm Keycloak |
| **BDD (aceite)** | Cucumber (`CucumberTest`) | cenários `@RFxx` ponta a ponta | as 23 features em `src/test/resources/features/` |
| **Arquitetura** | `ModularityTest` | fronteiras de módulo em todo build | `ApplicationModules.verify()` |
| **Avaliação (RF33)** | runner de golden set (fora do build padrão) | qualidade de retrieval, comparação GraphRAG × vetorial | §4 |

Regra de custo: o build padrão (`./mvnw test`) roda unitário + módulo + arquitetura + BDD ativo — e precisa continuar **rápido e verde**. Testes que sobem containers pesados (Ollama, Docling) são tageados e rodam sob demanda/perfil próprio.

## 2. Fluxo BDD — como um RF "fecha"

Estado atual: 23 features, todas `@pendente`; o runner filtra `not @pendente` (build verde com zero cenários ativos).

1. Implementar o RF no módulo correspondente.
2. Substituir o `PendingException` das steps (`com.github.overz.bdd.steps`) pela automação real.
3. Remover `@pendente` do cenário (ou da feature, quando toda ela passar).
4. `./mvnw test` — o cenário executa e precisa passar; `ModularityTest` continua guardando fronteiras.

**DoD padrão de toda tarefa do backlog:** cenários `@RFxx` correspondentes passando sem `@pendente` (regra do [`rag-plan.md`](../rag-plan.md) §6).

Infra das steps quando o contexto Spring for necessário: classe `@CucumberContextConfiguration` + `@SpringBootTest(webEnvironment = RANDOM_PORT)` no pacote `com.github.overz.bdd`, importando a `TestcontainersConfiguration` — `cucumber-spring` já está no classpath.

## 3. Infraestrutura de teste (Testcontainers)

- `TestcontainersConfiguration` (migrada para a API 2.x — débito [0.2]) provê: **Postgres**, **Neo4j**, **OpenSearch**, **Keycloak** (imagem oficial com import do mesmo `graphrag-realm.json` do compose — um realm só, dev e teste idênticos) e o container LGTM existente.
- **Ollama/Docling/GLiNER não sobem no build padrão** — são pesados e não determinísticos. Nos testes, as portas correspondentes (`ChatModel`, `EmbeddingModel`, `NerClient`, leitor Docling) recebem stubs determinísticos (embedding fake por hash do texto, extrator de entidades fixo por fixture). Os cenários BDD validam **orquestração e regras** (estados, filtros, isolamento, idempotência), não a qualidade do modelo — qualidade é papel do golden set (§4).
- **Tokens nos cenários BDD (mitigação do risco R13):** as steps obtêm JWT reais do Keycloak de teste via password grant dos usuários seedados; um cache de token por usuário na execução da suíte evita centenas de round-trips.
- Dev-mode (`./mvnw spring-boot:test-run` / `TestApplication`) usa a mesma configuração — subir a app sem Docker Compose continua funcionando.

## 4. Golden set e avaliação de recuperação (RF33)

Detalhe do formato/execução em `consulta.md` §7; aqui, a governança:

- **Corpus-semente versionado** (`docs/golden-set/corpus/`): 10–20 arquivos, pt-BR + inglês, cobrindo todos os formatos do RF04 (incluindo PDF escaneado para exercitar OCR). É fixado cedo (fase 3 do roadmap) porque ancora três validações: qualidade do embedding em pt-BR ([5.4]), ganho do GraphRAG sobre só-vetorial (RF33) e regressões de chunking/limiar.
- **Execução manual/por demanda** (runner tageado): compara os dois modos e emite relatório versionado em `docs/golden-set/reports/`. Não entra no build — depende do Ollama real.
- **Critério de decisão do [5.4]:** se recall@10 em consultas pt-BR ficar materialmente abaixo das equivalentes em inglês no mesmo corpus, troca-se o modelo de embedding (runbook de reindexação em `resiliencia-e-operacao.md` §7) e registra-se ADR.

## 5. Determinismo com LLM local

- Extração (RF21): temperatura 0 + structured output + validação de schema — o teste de integração valida o **contrato** (schema respeitado, fora-de-schema descartado), nunca o conteúdo exato.
- Geração (RF26): cenários BDD assertam propriedades estruturais (citações presentes e resolvíveis, `degraded` correto, admissão de não saber com contexto vazio — que é determinística por curto-circuito), não o texto da resposta.
- Regra geral: **testes assertam invariantes; o golden set mede qualidade.** Confundir os dois gera suíte frágil ou qualidade não medida.

## 6. Decisões registradas nesta seção

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Stubs determinísticos de IA no build padrão; modelos reais só no golden set | Ollama no Testcontainers do build | build lento e não determinístico; BDD valida regra, não modelo |
| Mesmo realm JSON para compose e Testcontainers | realm de teste separado | divergência dev×teste é bug esperando; um artefato, duas cargas |
| Cache de token por usuário na suíte | token por cenário | centenas de round-trips ao Keycloak (risco R13) |
| Golden set fora do build, relatórios versionados | métrica de qualidade como teste de CI | qualidade flutua com modelo/corpus; gate binário geraria vermelho falso |
