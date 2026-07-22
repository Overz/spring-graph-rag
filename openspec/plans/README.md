# Planos por épico — visão geral

Um arquivo por épico (`PLAN-00.md` a `PLAN-10.md`, um por `### Épico N` do antigo
`docs/rag-plan.md`, hoje removido) — narrativa + lista de tarefas atômicas `[N.M]`.
Conteúdo transversal (não específico de um único épico) preservado abaixo.

## Roadmap sugerido

Cada fase agrupa épicos que fazem sentido juntos — ritmo de estudo, não sprint corporativa:

| Fase | Foco | Épicos |
|---|---|---|
| **1 — Fundação** | Débitos zerados, schema correto, Keycloak no ar (SDD ✅ já escrito) | 0 |
| **2 — Entrada confiável** | Upload validado, deduplicado, armazenado; ciclo de vida e eventos rodando | 1, 2 (2.1–2.2), 3 (3.1–3.3) |
| **3 — Documento → vetor** | Extração/OCR, Markdown, chunking hierárquico, embeddings indexados | 4, 5 |
| **4 — Primeira resposta** | Consulta vetorial + geração fundamentada com fontes (ainda sem grafo) | 7 (7.1 parcial, 7.2, 7.4) |
| **5 — GraphRAG de verdade** | Grafo, entity resolution, travessia + RRF, MCP, golden set | 6, 7 (completo) |
| **6 — Vida real** | Soft delete/versão/GC, resiliência completa, fair queueing, NATS | 2 (2.3–2.5), 3 (3.4–3.5), 8 |
| **7 — Fortaleza** | Endurecimento: ADR/TLS/criptografia, auditoria, LGPD, prompt injection | 9 |
| **futuro** | Observabilidade aprofundada e metas de RNF | 10 |

**MVP (primeira resposta útil):** fim da fase 4 — pergunta respondida com base em
documento real, com fontes. Isso é *deliberadamente* um GraphRAG incompleto: o
próprio RF08 admite documentos `PARTIALLY_COMPLETED` (vetor sem grafo) como
estado válido e útil, então o ramo `GRAPH_BUILDING` pode nascer stub e o sistema
já entrega valor. O grafo (fase 5) multiplica o que existe — e o golden set
(RF33) mede objetivamente esse ganho, em vez de assumi-lo.

**Não deixar para depois:** `tenantId`/`ownerId`/`isActive` em todo filtro e
todo modelo desde a fase 2 — retrofit de multitenancy é o erro mais caro deste
tipo de sistema. Com a ADL-008, a identidade já é real desde a fase 1
(JWT/Keycloak, [0.7]): tenant e dono vêm sempre das claims, nunca de mock.

## Riscos e pontos de atenção

| Risco | Impacto | Mitigação |
|---|---|---|
| Hardware local apertado (Ollama + OpenSearch + Neo4j + Docling simultâneos) | Alto | Limites por serviço já dimensionados no `compose.yaml`; heap reduzido nas JVMs; modelos quantizados; CPU-only funciona, só mais lento. |
| `nomic-embed-text` insuficiente em pt-BR | Alto (silencioso) | [5.4] mede com golden set antes do volume; troca de modelo exige reindexação — decidir cedo. |
| Grafo fragmentado sem entity resolution | Alto (silencioso) | [6.4] é Must, não polimento: travessia multi-hop não acha caminho se o meio está duplicado em dois nós. |
| Spring AI 2.0 GA recente (poucas semanas) | Médio | Validar contra a documentação oficial antes de copiar exemplos antigos; APIs de tool calling mudaram da 1.x para a 2.0. |
| Integração NATS + Spring pouco batida (binder/cliente) | Médio | [3.4] começa com spike + ADR; os eventos internos do Modulith seguram o pipeline até lá. |
| Escritas duplas (OpenSearch + Neo4j) divergirem | Médio | Reconciliação periódica ([8.5]) + `openSearchId` obrigatório ([6.3]) + avaliar outbox. |
| `vm.max_map_count` no Linux derruba o OpenSearch na subida | Baixo (mas rouba uma tarde) | `sudo sysctl -w vm.max_map_count=262144` antes do compose (documentado no próprio `compose.yaml`). |
| Filtro de tenant esquecido em query nova | Alto (vazamento) | Isolamento estrutural (âncora no modelo) + cenários BDD de `seguranca/multitenant.feature` + auditoria de queries na revisão. |
| Escopo inflar além dos RFs | Médio | Sem RF, não entra em épico — ideias vão para "melhorias futuras" (abaixo). |
| Keycloak como dependência dura de toda a suíte BDD (token por cenário) | Médio | Realm de teste mínimo versionado (o mesmo do compose) + cache de token por usuário na execução — ver `docs/sdd/qualidade-e-testes.md`. |

**Melhorias futuras (sem RF — não são backlog):** sanitização/mascaramento de
PII pré-indexação, curadoria manual assistida por IA (auto-tagging), interface
web de chat, re-ranking com cross-encoder, RAPTOR, cache semântico de
respostas, suporte a DOCX/XLSX/PPTX (este último citado como avaliação no
RF-04). Se algum se provar necessário, primeiro vira RF em
`openspec/requirements/`, depois entra aqui.

## Referências

- Requisitos: [`openspec/requirements/`](../requirements/) · SDD: [`docs/sdd.md`](../../docs/sdd.md) + [`docs/sdd/`](../../docs/sdd/)
- ADRs: [`openspec/decisions/`](../decisions/)
- Spring AI: https://docs.spring.io/spring-ai/reference/ · Spring Boot: https://docs.spring.io/spring-boot/ · Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- Ollama (modelos e tool calling): https://ollama.com/library · https://docs.ollama.com/capabilities/tool-calling
- OpenSearch: https://opensearch.org/docs/latest/ · Neo4j: https://neo4j.com/docs/ · NATS: https://docs.nats.io/
- MinIO: https://min.io/docs/ · JuiceFS: https://juicefs.com/docs/community/introduction/
- Docling Serve: https://github.com/docling-project/docling-serve · Docling Java: https://github.com/docling-project/docling-java
- ClamAV: https://docs.clamav.net/ · Keycloak: https://www.keycloak.org/documentation · Resilience4j: https://resilience4j.readme.io/
- OWASP Top 10 for LLM Applications (LLM01 — Prompt Injection): https://owasp.org/www-project-top-10-for-large-language-model-applications/
- Cucumber JVM (BDD): https://cucumber.io/docs/cucumber/
