Mapeia RF08–RF11 / `docs/rag-plan.md` Épico 2 ([2.1]–[2.5]). **Antes de iniciar a implementação**, confirmar as Open Questions do `design.md` com o usuário (endpoints, nome da porta de índice vetorial, granularidade das capabilities, e principalmente D2 — soft-delete síncrono).

## 1. Decisões de arquitetura a confirmar antes de codar

- [ ] 1.1 Confirmar D2 (soft-delete síncrono, não event-driven) — decisão de maior impacto deste change
- [ ] 1.2 Confirmar paths/verbos dos endpoints novos (design.md Open Question 1)
- [ ] 1.3 Confirmar nome da porta de índice vetorial (design.md Open Question 2 — `VectorIndex`/`OpenSearchVectorIndex` ou outro)
- [ ] 1.4 Confirmar granularidade das 3 capabilities propostas (Open Question 3)

## 2. Schema Neo4j e OpenSearch (estrutura, não população)

- [ ] 2.1 `ApplicationRunner` idempotente no `rag` executando as constraints/índices de `dados.md` §4 (`document_id`, `chunk_id`, `chunk_os_id`, `entity_id`, `entity_canonical`, `chunk_tenant_active`, `entity_tenant_type`, vector index `entity_name_embedding`) — todas `IF NOT EXISTS` (design.md D3)
- [ ] 2.2 Índice OpenSearch mínimo de chunk (`chunkId`, `documentId`, `ownerId`, `tenantId`, `isActive` como `keyword`; `content` como `text` — `dados.md` §3), criado de forma idempotente (checar existência antes de criar)
- [ ] 2.3 `TestcontainersConfiguration`: confirmar que Neo4j/OpenSearch já testcontainerizados (Épico 0) cobrem os testes deste change sem ajuste adicional

## 3. Modelo e repositórios

- [ ] 3.1 Repositórios Neo4j em `rag/internal/repositories/` para `Document`/`Chunk`/`Entity` (leitura/escrita mínima: marcar `isActive`, consultar órfãos, criar fixture de teste)
- [ ] 3.2 Porta + adapter do índice vetorial (nome confirmado em 1.3) em `rag/internal/repositories/` — método mínimo: inativar vetores por `chunkId`/`documentId`
- [ ] 3.3 `DocumentLifecycleService` (`rag/internal/services/`): único ponto de escrita de `DocumentStatus`; deriva status agregado de `embeddingStatus`/`graphStatus`; grava `document_status_history` em cada transição

## 4. `DocumentCommandApi` e comandos

- [ ] 4.1 Consulta de status atual + histórico completo (RF09) — método(s) novos na porta pública, documento de outro tenant/dono responde vazio (mapeado pra 404 limpo em `api`)
- [ ] 4.2 Comando de exclusão lógica (RF10): valida dono (RF30), marca `is_active=false` no Postgres, chama os repositórios Neo4j/OpenSearch (síncrono, D2), registra erro em `processing_errors` se algum store falhar
- [ ] 4.3 Comando de substituição de versão (RF10 complemento): exclusão lógica da versão anterior + registro da nova versão (`version+1`) reiniciando em `UPLOADED`

## 5. Endpoints REST (`api`)

- [ ] 5.1 `DocumentLifecycleController` novo (separado do `DocumentUploadController`) com os 4 endpoints confirmados em 1.2
- [ ] 5.2 Autorização: rota de exclusão exige dono (RF30); demais rotas de consulta seguem o padrão de `CallerContext` já estabelecido

## 6. Garbage Collection (RF11)

- [ ] 6.1 Query Cypher de entidades órfãs (sem aresta `MENTIONS` para `Chunk` `isActive=true`) e remoção física (nó + relacionamentos)
- [ ] 6.2 Job agendado (`@Scheduled`, intervalo configurável em `application.yaml` — `app.gc.*`)

## 7. BDD — fechar as features de ciclo de vida

- [ ] 7.1 Automatizar `ciclo-de-vida/status-e-historico.feature` (RF08 esqueleto via simulação direta de transição no `DocumentLifecycleService`, RF09 completo) — remover `@pendente` de todos os cenários
- [ ] 7.2 Automatizar `ciclo-de-vida/soft-delete-e-versionamento.feature` (RF10, fixture Neo4j/OpenSearch direto nos steps `Dado`) — remover `@pendente` de todos os cenários
- [ ] 7.3 Automatizar `ciclo-de-vida/garbage-collection.feature` (RF11, fixture de órfão direto nos steps `Dado`) — remover `@pendente` de todos os cenários
- [ ] 7.4 `./mvnw test` verde (`ModularityTest` + cenários ativos)

## 8. Documentação

- [ ] 8.1 `docs/sdd/dados.md` §5 — nota que `SoftDeleteRequestedEvent` é desenho documentado, implementação síncrona até o Épico 3 (design.md D2)
- [ ] 8.2 `docs/rag-plan.md` Épico 2 — marcar [2.1]–[2.5] como feito, referenciar este change
- [ ] 8.3 `CLAUDE.md` — seção "Current implementation stage"
- [ ] 8.4 `openspec archive` ao final, sincronizar as 3 specs novas em `openspec/specs/`
