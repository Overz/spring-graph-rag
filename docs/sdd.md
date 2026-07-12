# SDD — Software Design Document — Plataforma GraphRAG Local

> Este documento responde **como** o sistema é desenhado. Ele é o índice principal do design: a documentação arquitetural detalhada vive em [`docs/sdd/`](sdd/), dividida por domínio, com diagramas Mermaid (C4 até nível C3).

**Status:** em construção, seção a seção, com validação a cada etapa
**Última atualização:** Julho/2026

---

## 1. Papel deste documento

| Documento                        | Pergunta que responde                       | Papel                                                                |
|----------------------------------|---------------------------------------------|----------------------------------------------------------------------|
| [`requisitos.md`](requisitos.md) | **O quê** o sistema deve fazer              | Fonte da verdade: RF01–RF39 + RNF01–04                               |
| [`rag-plan.md`](rag-plan.md)     | **Em que ordem e em que pedaços** construir | Backlog: épicos ↔ RFs ↔ features BDD                                 |
| `sdd.md` + [`sdd/`](sdd/) (este) | **Como** cada pedaço é desenhado            | Design detalhado: C4, contratos, modelos de dados, decisões técnicas |
| [`adr/`](adr/)                   | **Por que** decisões pontuais foram tomadas | Decisões de arquitetura com contexto e alternativas                  |
| `src/test/resources/features/`   | **Quando** um requisito está pronto         | Critérios de aceite executáveis (Gherkin, tags `@RFxx`)              |

**Regra de coerência:** requisitos vencem o SDD; o SDD vence a memória — se a implementação divergir do desenho, ou o desenho estava errado (atualize-o) ou o código está (corrija-o). Decisões *por quê* significativas continuam indo para `adr/`, referenciadas daqui.

## 2. Como este SDD foi produzido

O design foi definido em uma **sessão de descoberta arquitetural** (julho/2026) sobre as fontes oficiais — `requisitos.md`, `rag-plan.md`, ADRs 001–003, a suíte BDD e o repositório — questionando premissas e registrando cada decisão no *Architecture Decision Log* (seção 4). As decisões marcadas "ADR pendente" ganham ADR formal quando o épico correspondente chegar.

## 3. Premissas de design

Herdam e substituem o esboço original deste arquivo:

1. **Tudo local e sem custos.** Nenhuma dependência paga ou chave de API externa; `compose.yaml` é a fonte da verdade da infraestrutura.
2. **A execução local simula um ambiente cloud completo**: upload de arquivos, eventos, bases de dados, resiliência e documentação (ADRs + C4 até C3).
3. **Mensageria:** eventos internos (Spring Modulith) primeiro; **NATS** como broker externo quando a fila real for necessária — sem Kafka, por não haver necessidade dessa complexidade.
4. **Monitoração: postergada.** O wiring OTel/LGTM existente permanece; dashboards, métricas por etapa e metas de RNF ficam para o Épico 10.
5. **LLM local** (Ollama) com limites de recursos coerentes no `compose.yaml` para conviver com os demais serviços.
6. **Embeddings devem atender pt-BR e inglês** — validação com dados antes do volume (tarefa [5.4] do plano).
7. **BDD é o critério de aceite:** o design de cada domínio referencia os cenários que o validam.

## 4. Architecture Decision Log (ADL)

Decisões tomadas na descoberta. `Fonte` = onde a decisão está fundamentada; `ADR` = formalização pendente ou existente.

| ID      | Decisão                                                                                                                                                                                                                                                     | Fonte / ADR                                             |
|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| ADL-001 | Storage de artefatos atrás da porta `DocumentStorage`; JuiceFS + MinIO + Redis; estágios RAW/TRANSFORMED                                                                                                                                                    | [ADR-001](adr/ADR-001-storage-artefatos-documento.md)   |
| ADL-002 | Docling Serve (CPU) para PDF/imagem/OCR; Tika para demais formatos e detecção de MIME                                                                                                                                                                       | [ADR-002](adr/ADR-002-parsing-pdf-imagem-docling.md)    |
| ADL-003 | Embeddings via Ollama; `nomic-embed-text` inicial, validado contra golden set pt-BR/EN antes do volume                                                                                                                                                      | [ADR-003](adr/ADR-003-embeddings-via-ollama.md) + [5.4] |
| ADL-004 | NATS como broker externo, precedido pelos eventos internos do Modulith                                                                                                                                                                                      | Premissa 3; ADR pendente ([3.4])                        |
| ADL-005 | Defaults de retrieval: RRF `k=60`, travessia 1–2 hops, merge de entity resolution > 0.85 — pontos de partida ajustáveis por medição                                                                                                                         | `requisitos.md` (RF25/RF32)                             |
| ADL-006 | **NER via GLiNER em sidecar CPU** (zero-shot, rótulos = ontologia do RF21), usado no pipeline e no NER de consulta; spike + ADR no Épico 6; fallback: extração só-LLM                                                                                       | Descoberta; ADR pendente                                |
| ADL-007 | **Consulta stateless** (REST/MCP); conversação multi-turno = ponto de extensão documentado (contrato de sessão, contexto conversacional, reescrita de consulta), sem implementação nem dependência de chat memory nesta fase                                | Descoberta                                              |
| ADL-008 | **Autenticação JWT + Keycloak desde o dia 1**: realm único `graphrag`, claim `tenantId` (atributo + protocol mapper), `ownerId` = `sub`; Keycloak na última versão, buildado e com realm JSON versionado/importado no start (padrão de referência validado) | Descoberta; ADR pendente (Épico 9)                      |
| ADL-009 | **Resposta de consulta síncrona e completa** (JSON: resposta + citações + metadados + `correlationId`); SSE/streaming = ponto de extensão                                                                                                                   | Descoberta                                              |
| ADL-010 | SDD físico: `sdd.md` índice + `docs/sdd/` por domínio, diagramas Mermaid, ADRs separados em `docs/adr/`                                                                                                                                                     | Descoberta                                              |

**Defaults técnicos** (decididos no documento de domínio indicado, com alternativas e justificativa): tokenizer de chunking e semântica do payload acumulado (→ `ingestao.md`/`extracao-e-vetorial.md`); DLQ pré-NATS e superfície administrativa (→ `resiliencia-e-operacao.md`); embedding de entidade para entity resolution (→ `knowledge-graph.md`); orçamento de contexto da geração (→ `consulta.md`); criptografia em repouso real × simulada e modelo de roles (→ `seguranca.md`); backup de volumes (→ `resiliencia-e-operacao.md`).

## 5. Índice — documentos por domínio

| Documento                                                        | Domínio                                                                                                 | RFs principais               | Status    |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|------------------------------|-----------|
| [`sdd/arquitetura.md`](sdd/arquitetura.md)                       | C4 (C1–C3), módulos Spring Modulith, fluxos de runtime, erros/logging, pontos de extensão               | transversal                  | ✅ escrito |
| [`sdd/ingestao.md`](sdd/ingestao.md)                             | Upload, validações, malware, idempotência, storage RAW, metadados, cotas, ciclo de vida, eventos e fila | RF01–RF13, RF39              | ✅ escrito |
| [`sdd/extracao-e-vetorial.md`](sdd/extracao-e-vetorial.md)       | Delegação por MIME, extração/OCR, Markdown, chunking hierárquico, embeddings, indexação                 | RF14–RF20                    | ✅ escrito |
| [`sdd/knowledge-graph.md`](sdd/knowledge-graph.md)               | Ontologia, extração híbrida (GLiNER+LLM), construção do grafo, entity resolution, GC                    | RF21–RF24, RF32, RF11        | ✅ escrito |
| [`sdd/consulta.md`](sdd/consulta.md)                             | Retrieval híbrido, RRF, geração fundamentada, MCP, golden set, extensões (SSE, multi-turno)             | RF25, RF26, RF33             | ✅ escrito |
| [`sdd/dados.md`](sdd/dados.md)                                   | Schemas PostgreSQL/OpenSearch/Neo4j, contratos de evento, convenções de chave                           | transversal                  | ✅ escrito |
| [`sdd/seguranca.md`](sdd/seguranca.md)                           | Keycloak/JWT, AuthZ multitenant, auditoria, prompt injection, criptografia, LGPD                        | RF30, RF31, RF34–RF36        | ✅ escrito |
| [`sdd/resiliencia-e-operacao.md`](sdd/resiliencia-e-operacao.md) | Falha por etapa, retry/DLQ, circuit breaker, reconciliação, backup, operação local                      | RF27–RF29, RF37, RF38, RNF03 | ✅ escrito |
| [`sdd/qualidade-e-testes.md`](sdd/qualidade-e-testes.md)         | Estratégia BDD, Testcontainers, golden set/avaliação, testes de módulo                                  | RF33 + suíte BDD             | ✅ escrito |

## 6. Convenções de leitura

- **Diagramas:** Mermaid (renderizam no GitHub). C1/C2 usam a sintaxe C4 do Mermaid; C3 usa diagramas de componente/fluxo por legibilidade.
- **Vocabulário de estados:** sempre o ciclo do RF08 (`RECEIVED → ... → COMPLETED | PARTIALLY_COMPLETED | FAILED`), com sub-estados `embeddingStatus`/`graphStatus` no fork-join.
- **Status de infra:** serviços marcados *(pendente)* ainda não existem no `compose.yaml` — a tabela da seção 4.1 do [`rag-plan.md`](rag-plan.md) diz em qual épico entram.
- Nomes de classe/componente nos C3 são **indicativos** (contratos e responsabilidades valem; nomes exatos podem variar na implementação, mantendo as convenções do projeto).
