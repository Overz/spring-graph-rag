Mapeia RF40 (novo) + revisão de RF09 (`docs/requisitos.md`). Todas as decisões
confirmadas com o usuário em 2026-07-22 — ver `design.md`.

## 1. Schema

- [x] 1.1 Migration `V3__document_updated_at.sql` — coluna `updated_at`, backfill com
      `uploaded_at` para linhas existentes
- [x] 1.2 `DocumentEntity.updatedAt` + `@PrePersist`/`@PreUpdate` (D3)

## 2. Repositório e API pública

- [x] 2.1 `DocumentRepository.findByTenantId`/`findByTenantIdAndActiveTrue` (paginados)
- [x] 2.2 Novo record público `DocumentSummary` (`rag`)
- [x] 2.3 `DocumentCommandApi.listDocuments(tenantId, includeInactive, Pageable)`
- [x] 2.4 `DocumentCommandApi.statusOf`/`historyOf` perdem parâmetro `ownerId` (D1)

## 3. Serviço

- [x] 3.1 `DocumentLifecycleService.listDocuments` — mapeia `DocumentEntity` → `DocumentSummary`
- [x] 3.2 `findVisibleTo`/`findAccessibleTo` sem filtro de dono (mantêm tenantId
      +isActive/tenantId conforme já valia antes)

## 4. Endpoint REST

- [x] 4.1 `DocumentQueryController` (`GET /api/v1/documents`) + `DocumentQueryConfig` (D5)
- [x] 4.2 DTOs `DocumentSummaryResponse`/`PagedResponse<T>` + `DocumentQueryResponseMapper`
- [x] 4.3 `DocumentLifecycleController` atualizado pras novas assinaturas sem `ownerId`

## 5. Documentação

- [x] 5.1 `requisitos.md`: RF40 novo + revisão de RF09 (visibilidade tenant-wide)

## 6. BDD

- [x] 6.1 `listagem-de-documentos.feature`: ativo por padrão, `includeInactive`,
      compartilhado no tenant, isolamento de tenant (RF30), paginação
- [x] 6.2 `status-e-historico.feature`: cenário de histórico cross-owner corrigido
      (sucesso, não mais 404) + novo cenário de histórico cross-tenant (continua 404)
- [x] 6.3 `@Before` de limpeza de estado (`CicloDeVidaSteps`) estendido pra `@RF40`
- [x] 6.4 `./mvnw test` verde (167 cenários, 0 falhas)

## 7. Arquivamento

- [x] 7.1 `openspec archive` + sync de `listagem-de-documentos` (novo) e
      `ciclo-de-vida-documento` (revisão de visibilidade) em `openspec/specs/`
