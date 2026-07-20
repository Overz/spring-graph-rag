## Why

O upload (Épico 1) só cobre `RECEIVED → VALIDATING → UPLOADED`; o resto do ciclo de vida do RF08 (fila, extração, transformação, chunking, o fork-join `EMBEDDING`/`GRAPH_BUILDING`, e a derivação `COMPLETED`/`PARTIALLY_COMPLETED`/`FAILED`) não tem nenhuma lógica implementada, apesar de o schema Postgres (`documents.embedding_status`/`graph_status`, `document_status_history.branch`) já ter sido desenhado pra isso desde o Épico 0. Sem RF09 (status/histórico), o usuário não tem visibilidade nenhuma do que acontece com o documento depois do aceite. Sem RF10/RF11, não existe exclusão lógica nem limpeza de grafo — pré-requisitos antes de qualquer dado real entrar em Neo4j/OpenSearch nos Épicos 5/6.

## What Changes

- `DocumentLifecycleService` (novo, módulo `rag`): único ponto de escrita de `DocumentStatus`, deriva o status agregado a partir dos sub-estados `embeddingStatus`/`graphStatus` (RF08). Testável hoje via simulação direta de transição — não há pipeline real (Épicos 3–6) disparando as etapas ainda.
- `DocumentCommandApi` ganha consulta de status atual + histórico completo (RF09) e comando de exclusão lógica/nova versão (RF10) — únicos métodos novos na porta pública que `api` consome.
- Schema Neo4j (`Document`/`Chunk`/`Entity`, constraints/índices de `docs/sdd/dados.md` §4) e um índice OpenSearch mínimo de chunk (§3) criados neste change — **estrutura**, não população: nenhuma extração de entidade (Épico 6) ou geração de embedding (Épico 5) acontece aqui. Cenários de RF10/RF11 usam fixture direta nos steps `Dado` para simular que o dado já existe.
- Novos endpoints REST em `api`: consulta de status, consulta de histórico, exclusão lógica, substituição de versão (paths exatos — ver Open Questions do `design.md`).
- Job de garbage collection (RF11) que varre o Neo4j por entidades/relacionamentos sem chunk ativo conectado e remove fisicamente.
- **BREAKING**: nenhum — tudo aditivo sobre o que existe.

## Capabilities

### New Capabilities
- `ciclo-de-vida-documento`: máquina de estados RF08 (fork-join, sub-estados, derivação do status agregado) e consulta de status/histórico (RF09).
- `exclusao-e-versionamento`: exclusão lógica com isolamento de grafo e substituição de versão (RF10 + RF30 no cenário de autorização).
- `garbage-collection-grafo`: expurgo físico de entidades/relacionamentos órfãos (RF11).

### Modified Capabilities
- Nenhuma. RF01–RF07 (`upload-de-documentos`, `validacao-de-ingestao`) não mudam de comportamento.

## Impact

- **Código:** novo `DocumentLifecycleService`, extensão de `DocumentCommandApi`/`RegisteredDocument` (ou record novo), repositórios Neo4j (`rag/internal/repositories`), porta + adapter mínimo de índice vetorial (nome a definir — ver design.md), 4 endpoints REST novos em `api/internal/controllers`, job de GC agendado.
- **Infra:** nenhuma mudança em `compose.yaml` — Neo4j e OpenSearch já existem no compose desde o Épico 0, só ficam com uso real por trás pela primeira vez.
- **Schema:** constraints/índices Neo4j (idempotentes, `IF NOT EXISTS`) e mapping de índice OpenSearch — mecanismo de criação a decidir (ver design.md Open Questions). Nenhuma migração Postgres nova — schema já 100% pronto desde `V1__baseline_documents.sql`.
- **Docs:** `docs/sdd/dados.md` (nota do `SoftDeleteRequestedEvent` como débito até o Épico 3), `docs/rag-plan.md` [2.1]–[2.5], `CLAUDE.md`.
- **Fora de escopo deste change:** extração de entidade real (Épico 6), geração de embedding real (Épico 5), fila de eventos (Épico 3) — o `SoftDeleteRequestedEvent` de `dados.md` §5 fica como desenho documentado, não implementado ainda (ver design.md).
