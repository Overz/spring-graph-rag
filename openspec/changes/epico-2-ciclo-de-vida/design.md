## Context

Design canônico: `docs/sdd/dados.md` §§2–4 (schema Postgres/OpenSearch/Neo4j) e `docs/rag-plan.md` Épico 2 ([2.1]–[2.5]) — não repetidos aqui. Estado atual verificado no repositório (não só no SDD):

- `DocumentStatus` (enum público `rag`) já lista todos os estados do RF08 fork-join — nada a adicionar no enum.
- `documents` (Postgres, `V1__baseline_documents.sql`) já tem `embedding_status`, `graph_status`, `version`, `is_active`, `lgpd_redacted`; `document_status_history` já tem `branch`. `DocumentEntity` (JPA) já mapeia todos esses campos. **Nenhuma migração Postgres nova é necessária neste change.**
- `DocumentCommandApi` hoje só tem `registerAcceptedUpload`/`successfulDuplicateExists`/`quotaOf`/`usageOf` — sem consulta de status/histórico, sem comando de exclusão.
- Não existe `DocumentLifecycleService`; `DocumentIngestService` só cobre `RECEIVED→VALIDATING→UPLOADED`.
- Não existe nenhum uso de Neo4j ou OpenSearch no código ainda — só existem como serviços no `compose.yaml`/`TestcontainersConfiguration`, sem nenhum client/repositório escrito.
- Nenhum evento de domínio é publicado/consumido em lugar nenhum do código hoje: `DocumentUploadService` chama `DocumentCommandApi` direto (síncrono). A prosa do `CLAUDE.md` ("pipeline 100% event-driven") descreve a fronteira `api → rag`, mas a infra de publicação de eventos internos do Épico 3 ([3.1]) ainda não foi construída.
- `docs/sdd/dados.md` §5 já especifica `SoftDeleteRequestedEvent` (evento → 3 listeners independentes: relacional/vetorial/grafo) como o mecanismo de soft-delete — pressupõe a infra do Épico 3.

## Goals / Non-Goals

**Goals:**
- Máquina de estado (RF08) com sub-estados independentes e status agregado derivado, testável via simulação direta de transição (sem depender de pipeline real).
- Consulta de status atual + histórico completo (RF09), 100% funcional hoje.
- Schema Neo4j (`Document`/`Chunk`/`Entity`) e índice OpenSearch mínimo de chunk, criados agora — estrutura pronta pros Épicos 5/6 populares sem redesenho.
- Exclusão lógica com isolamento de grafo (RF10) e substituição de versão (RF10 complemento), testadas contra o schema acima via fixture.
- Garbage collection de entidades/relacionamentos órfãos (RF11), testado contra o mesmo schema via fixture.

**Non-Goals (deste change):**
- Extração de entidade real via NER/LLM (Épico 6) — só a estrutura que a receberá.
- Geração de embedding real (Épico 5) — só o índice que a receberá.
- Fila de eventos interna (Épico 3, RF12/13) — soft-delete fica síncrono por decisão (D2 abaixo), não event-driven.
- Reprocessamento real de pipeline ao substituir versão — não existe pipeline de extração/chunking pra reexecutar ainda; a nova versão só nasce em `UPLOADED`, igual ao upload original.

## Decisions

### D1 — Fixture direta nos steps BDD, não pipeline real

Cenários de RF08 ("progressão completa", "sub-estados independentes", falhas parciais/totais), RF10 (grafo/vetor) e RF11 (GC) descrevem estado que hoje só a IA real dos Épicos 5/6 produziria. Os steps `Dado` desses cenários gravam esse estado diretamente (Cypher/`Neo4jClient` para nós, chamada direta ao `DocumentLifecycleService` para simular "etapa X concluída") — mesmo padrão já usado pelo `SyntheticFiles` do Épico 1 (arquivo sintético em vez de esperar um arquivo real de terceiro). Consistente com `CLAUDE.md`: "deterministic AI stubs... golden set mede qualidade". Decisão confirmada com o usuário nesta sessão (3 rounds de pergunta) antes de abrir este change.

*Consequência:* quando os Épicos 5/6 chegarem, eles só escrevem no schema que já existe — nenhum redesenho, só troca do produtor do dado (de fixture de teste para extração/embedding real).

### D2 — Soft-delete síncrono agora, não event-driven

`dados.md` §5 especifica `SoftDeleteRequestedEvent` → 3 listeners (Postgres/Neo4j/OpenSearch) como o mecanismo de exclusão. Isso pressupõe a infra de eventos do Épico 3, que não existe. Este change implementa o comando de exclusão como uma chamada síncrona que atualiza os 3 stores diretamente (mesma transação lógica no `DocumentLifecycleService`) — mesmo padrão do ClamAV mock no Épico 1: débito registrado, adaptador troca depois sem redesenho de contrato.

*Alternativa considerada:* construir já a infra de evento (`ApplicationEventPublisher` + Modulith event publication registry) só para o soft-delete. Rejeitada — antecipa uma peça inteira do Épico 3 pra um único comando; melhor construir a infra de eventos de uma vez, quando o pipeline (RF12/13) realmente precisar dela para as 5+ etapas que ela vai orquestrar.

*Risco aceito:* se Neo4j ou OpenSearch falhar no meio da exclusão, o Postgres já pode estar marcado `is_active=false` — inconsistência temporária entre stores. Mitigação: registrar erro em `processing_errors` (schema já existe) e deixar para a reconciliação do Épico 8 (RF38) cobrir esse caso quando ela existir; não implementar retry próprio agora (duplicaria o que o Épico 3/8 vai fazer de forma melhor).

### D3 — Criação do schema Neo4j via `ApplicationRunner` idempotente

Não há Flyway para Neo4j neste projeto. As constraints/índices de `dados.md` §4 já são todas `IF NOT EXISTS` — um `ApplicationRunner`/`InitializingBean` no `rag` executa esse Cypher no start da aplicação, idempotente por natureza (rerun não tem efeito). Mesmo raciocínio para o índice OpenSearch (checar existência via API antes de criar).

*Alternativa considerada:* uma ferramenta de migração dedicada pra Neo4j (ex. Liquigraph). Rejeitada por agora — YAGNI; o conjunto de constraints é pequeno e estável, um runner idempotente resolve sem dependência nova. Revisitar se o esquema crescer muito nos Épicos 6+.

### D4 — Pacote/porta do código novo

- Repositórios Neo4j em `rag/internal/repositories/` (mesmo pacote dos repositórios JPA existentes — `DocumentRepository`, etc. — o projeto já usa esse pacote pra "acesso a dado", independente da tecnologia).
- Porta do índice vetorial nomeada por papel, mesmo padrão de `DocumentStorage`/`MalwareScanner`: `VectorIndex` (interface) + `OpenSearchVectorIndex` (adapter), em `rag/internal/repositories/` também (é uma porta de acesso a dado, não um serviço de negócio).
- `DocumentLifecycleService` em `rag/internal/services/` (mesmo pacote de `DocumentIngestService`).
- Novos endpoints em `api/internal/controllers/` — provavelmente um `DocumentLifecycleController` novo, separado de `DocumentUploadController` (responsabilidades distintas: aceitar upload vs. consultar/gerenciar ciclo de vida).

## Risks / Trade-offs

- **[Risco] Schema Neo4j/OpenSearch construído antes de ter um produtor real (Épico 5/6)** → Mitigação: schema vem 100% de `dados.md`, já revisado/estável; risco de retrabalho é baixo. Se a ontologia do Épico 6 mudar, é migração de dado, não de estrutura básica (`Document`/`Chunk`/`Entity`).
- **[Trade-off] D2 (soft-delete síncrono)** deixa uma janela de inconsistência entre stores em caso de falha parcial — aceito conscientemente, coberto por RF38 (Épico 8) quando existir.
- **[Risco] GC (RF11) roda contra dados que só existirão de verdade no Épico 6** → sem risco de dado real, mas a query Cypher de órfãos só é validada contra fixture agora; validar de novo com dado real de produção da extração quando o Épico 6 chegar.

## Migration Plan

Aditivo. Nenhuma rota/contrato existente muda. Rollback = remover o `DocumentLifecycleController`, o `ApplicationRunner` do schema Neo4j/índice OpenSearch e o job de GC — `documents`/`document_status_history` já existiam e continuam como estavam.

## Open Questions

1. **Paths/verbos dos endpoints novos** — proposta (confirmar): `GET /api/v1/documents/{id}/status` (status atual), `GET /api/v1/documents/{id}/history` (histórico completo), `DELETE /api/v1/documents/{id}` (exclusão lógica, RF10), `PUT /api/v1/documents/{id}` (multipart, substituição de versão — mesmo contrato de upload, mas em cima de um id existente). Alternativa a `PUT`: um verbo/path dedicado como `POST /api/v1/documents/{id}/versions`.
2. **Nome definitivo da porta de índice vetorial** — `VectorIndex`/`OpenSearchVectorIndex` (D4) ou outro nome preferido.
3. **Granularidade das capabilities no catálogo** — proposta: 3 capabilities (`ciclo-de-vida-documento`, `exclusao-e-versionamento`, `garbage-collection-grafo`). Podem ser agrupadas diferente se fizer mais sentido pro usuário.
4. **Confirmar D2 (soft-delete síncrono, não event-driven)** antes de implementar — é a decisão de maior impacto arquitetural deste change.
