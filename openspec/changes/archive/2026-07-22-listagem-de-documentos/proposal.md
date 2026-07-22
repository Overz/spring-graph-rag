## Why

`requisitos.md` (RF01–RF39) nunca cobriu listar documentos — só consulta por `id`
(RF09) existe. Usuário identificou o buraco em uso real: depois de enviar, consultar e
excluir um documento pelo id, não há como descobrir quais ids existem sem já sabê-los de
antemão. Gap de requisito genuíno, não item de épico esquecido.

## What Changes

- Novo `GET /api/v1/documents` paginado (RF40), compartilhado entre todos os usuários
  autenticados do tenant — não restrito ao dono de cada documento. Por padrão só traz
  documentos ativos (`isActive=true`); parâmetro `includeInactive` (padrão `false`) inclui
  também os excluídos logicamente. Cada item: id, filename, status, `ownerId` (quem
  enviou), version, createdAt, updatedAt.
- **RF09 revisado**: `GET /{id}/status` e `GET /{id}/history` deixam de ser restritos ao
  dono — qualquer usuário do mesmo tenant pode consultar (decisão confirmada com o
  usuário: a listagem tenant-wide só faz sentido se o usuário conseguir depois abrir o
  detalhe de um documento de outro dono que apareceu na lista). Isolamento entre tenants
  continua absoluto. `DELETE`/`POST /versions` continuam restritos ao dono (RF10/RF30) —
  não mudam.
- Nova coluna `documents.updated_at` (ausente até aqui — só existia `uploaded_at`),
  mantida automaticamente via `@PrePersist`/`@PreUpdate` na entidade.
- **BREAKING**: `DocumentCommandApi.statusOf`/`historyOf` perdem o parâmetro `ownerId`
  (interno ao `rag`, não afeta contrato HTTP externo — `api` já resolve `ownerId` via
  `CallerContext`, não via requisição).

## Capabilities

### New Capabilities
- `listagem-de-documentos`: listagem paginada, compartilhada no tenant, com filtro de
  inativos (RF40).

### Modified Capabilities
- `ciclo-de-vida-documento`: a regra de visibilidade de status/histórico (RF09) deixa de
  incluir `ownerId` — vira tenant-wide em vez de owner-scoped.

## Impact

- **Código:** `DocumentEntity` (+`updatedAt`), `DocumentRepository` (+2 finders
  paginados), novo record público `DocumentSummary` (`rag`), `DocumentCommandApi`
  (+`listDocuments`, assinatura de `statusOf`/`historyOf` simplificada),
  `DocumentLifecycleService` (implementação + `findVisibleTo`/`findAccessibleTo` sem
  filtro de dono), novo `DocumentQueryController`+`DocumentQueryConfig`, novos DTOs
  `DocumentSummaryResponse`/`PagedResponse<T>` + mapper.
- **Schema:** migration `V3__document_updated_at.sql` (coluna aditiva, sem quebra).
- **Docs:** `requisitos.md` (RF40 novo + revisão de RF09).
- **Fora de escopo:** filtros adicionais na listagem (por status, por nome, por dono) —
  só paginação + `includeInactive` por ora; pode crescer depois sem quebra de contrato.
