Mapeia RF08–RF11 / `docs/rag-plan.md` Épico 2 ([2.1]–[2.5]). Todas as decisões de arquitetura foram confirmadas com o usuário em 2026-07-20 — ver `design.md` §Open Questions (todas resolvidas).

## 1. Decisões de arquitetura (todas resolvidas)

- [x] 1.1 D2 — soft-delete síncrono agora, documentado como débito pro Épico 3
- [x] 1.2 Endpoints: `GET /{id}/status`, `GET /{id}/history`, `DELETE /{id}`, `POST /{id}/versions`
- [x] 1.3 `ChunkIndex`/`OpenSearchChunkIndex` — nomeado pelo domínio (Chunk), não pela tecnologia (Vector)
- [x] 1.4 3 capabilities separadas, granularidade máxima (preferência do usuário)

## 2. Schema Neo4j e OpenSearch (estrutura, não população)

- [x] 2.1 `ApplicationRunner` idempotente no `rag` executando as constraints/índices mínimos de `dados.md` §4 (`document_id`, `chunk_id`, `entity_id`, índice `chunk_tenant_active`) — `IF NOT EXISTS`; constraints Enterprise-only (`chunk_os_id`, `entity_canonical`, índice vetorial `entity_name_embedding`) deliberadamente fora de escopo até o Épico 6 criar os campos que dependem delas (design.md D3)
- [x] 2.2 Índice OpenSearch `chunks-v1`/alias `chunks` com o mapeamento completo de `dados.md` §3 (incl. `embedding` knn_vector), criado de forma idempotente (checar existência antes de criar)
- [x] 2.3 `TestcontainersConfiguration`: `OpensearchContainer` (`org.opensearch:opensearch-testcontainers`) adicionado — Neo4j já cobria os testes deste change sem ajuste

## 3. Modelo e repositórios (nomeados pelo domínio, método = ação exata — design.md D4)

- [x] 3.1 `DocumentGraphRepository` (`rag/internal/repositories/`): `markInactive(String documentId)` — marca `Document`+`Chunk`s como `isActive=false` (RF10). Implementado como classe sobre `Neo4jClient` direto (`Neo4jDocumentGraphRepository`), não `Neo4jRepository`+`@Query` — ver nota de implementação abaixo.
- [x] 3.2 `EntityGraphRepository` (`rag/internal/repositories/`): `findOrphanEntities()` + `deleteEntities(List<String> entityIds)` (RF11). Mesma nota: classe sobre `Neo4jClient` (`Neo4jEntityGraphRepository`).
- [x] 3.3 `ChunkIndex` (interface) + `OpenSearchChunkIndex` (adapter), `rag/internal/repositories/`: `inactivateByDocumentId(String documentId)` (RF10)
- [x] 3.4 `DocumentLifecycleService` (`rag/internal/services/`): único ponto de escrita de `DocumentStatus`; absorveu `DocumentIngestService` (Épico 1) — `registerAcceptedUpload`/`successfulDuplicateExists`/`quotaOf`/`usageOf` + os 4 métodos novos de RF09/RF10
- [x] 3.5 Fixtures de teste (Neo4j/OpenSearch) direto nos steps `Dado` de `CicloDeVidaSteps` — não entraram nas portas de produção, conforme decidido

> **Nota de implementação (não estava no plano original):** `Neo4jRepository<T,String>` com métodos `@Query` customizados (scalar projection `RETURN e.id`, ou Cypher com `WITH`/`OPTIONAL MATCH` encadeados) se mostrou instável no Spring Data Neo4j 8.1.0 usado neste projeto — um `NullPointerException` dentro de `Neo4jTemplate$DefaultExecutableQuery` para a query de `findOrphanEntities` (mesmo depois de trocar a projeção escalar por retorno de entidade — o problema não era o tipo de retorno, e sim o mecanismo de derivação de query em si para Cypher com subquery `EXISTS {}`), e um no-op silencioso (sem exceção, mas sem efeito) para `markInactive`. Resolvido convertendo as duas interfaces em classes escritas à mão sobre `Neo4jClient` (`Neo4jDocumentGraphRepository`/`Neo4jEntityGraphRepository`) — mesmo padrão já usado por `OpenSearchChunkIndex` sobre o cliente OpenSearch raw. Os `@Node` (`DocumentNode`/`ChunkNode`/`EntityNode`) ficaram sem uso depois dessa troca e foram removidos.

## 4. `DocumentCommandApi` e comandos

- [x] 4.1 Consulta de status atual + histórico completo (RF09) — `statusOf`/`historyOf`, documento de outro tenant/dono responde vazio (mapeado pra 404 limpo em `api`)
- [x] 4.2 Comando de exclusão lógica (RF10): valida dono (RF30), marca `is_active=false` no Postgres, chama `DocumentGraphRepository.markInactive` + `ChunkIndex.inactivateByDocumentId` (síncrono, D2), registra erro em `processing_errors` (nova `ProcessingErrorEntity`/`ProcessingErrorRepository`, tabela já existia desde o Épico 0) se algum store falhar
- [x] 4.3 Comando de substituição de versão (RF10 complemento): exclusão lógica da versão anterior + registro da nova versão (`version+1`, calculado a partir da versão anterior via o `id` do path, não do hash) reiniciando em `UPLOADED`

## 5. Endpoints REST (`api`)

- [x] 5.1 `DocumentLifecycleController` novo (separado do `DocumentUploadController`): `GET /api/v1/documents/{id}/status`, `GET /api/v1/documents/{id}/history`, `DELETE /api/v1/documents/{id}`, `POST /api/v1/documents/{id}/versions`
- [x] 5.2 Autorização: rota de exclusão exige dono (RF30, `DocumentAccessDeniedException` 403); rotas de consulta usam o mesmo `CallerContext` — 404 limpo (`DocumentNotFoundException`) tanto pra outro tenant quanto pra outro dono, sem role adicional no `SecurityConfig` (já cobertas por `anyRequest().authenticated()`)

## 6. Garbage Collection (RF11)

- [x] 6.1 Query Cypher de entidades órfãs (sem aresta `MENTIONS` para `Chunk` `isActive=true`) via `EntityGraphRepository.findOrphanEntities()`, remoção física via `deleteEntities(...)`
- [x] 6.2 Job agendado (`EntityGarbageCollectionJob`, `@Scheduled`, intervalo em `application.yaml` — `app.gc.interval-ms`)

## 7. BDD — fechar as features de ciclo de vida

- [x] 7.1 Automatizado `ciclo-de-vida/status-e-historico.feature` (RF08 esqueleto via simulação direta de transição em `CicloDeVidaSteps`, RF09 completo via HTTP real) — `@pendente` removido
- [x] 7.2 Automatizado `ciclo-de-vida/soft-delete-e-versionamento.feature` (RF10, fixture Neo4j/OpenSearch direto nos steps `Dado`) — `@pendente` removido
- [x] 7.3 Automatizado `ciclo-de-vida/garbage-collection.feature` (RF11, fixture de órfão direto nos steps `Dado`) — `@pendente` removido
- [x] 7.4 `./mvnw test` verde (`ModularityTest` + cenários ativos, 260 passos ✔, 0 erro)

## 8. Documentação

- [x] 8.1 `docs/sdd/dados.md` §5 — nota que `SoftDeleteRequestedEvent` é desenho documentado, implementação síncrona até o Épico 3 (design.md D2)
- [x] 8.2 `docs/rag-plan.md` Épico 2 — marcado [2.1]–[2.5] como feito, referenciando este change
- [x] 8.3 `CLAUDE.md` — seção "Current implementation stage" atualizada
- [x] 8.4 `openspec archive` ao final, sincronizar as 3 specs novas em `openspec/specs/`
