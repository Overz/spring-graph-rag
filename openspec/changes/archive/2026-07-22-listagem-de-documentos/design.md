## Decisões confirmadas com o usuário (2026-07-22)

### D1 — Visibilidade tenant-wide, não owner-scoped

A listagem mostra documentos de **qualquer usuário do tenant**, não só do dono
(confirmado explicitamente: "pode ser que outro usuário consiga acessar o mesmo
documento"). Consequência direta: `GET /{id}/status` e `GET /{id}/history` também abrem
pra leitura tenant-wide — senão a listagem mostraria documentos que o próprio usuário não
conseguiria abrir depois, UX quebrada. `DELETE`/`POST /versions` **não** mudam — só dono
exclui/versiona (RF10/RF30), com BDD já cobrindo isso.

*Risco aceito:* qualquer usuário autenticado do tenant lê status/histórico/conteúdo de
metadado de documentos de colegas. Sem RF explícito de "papéis" (admin vs. usuário comum)
ainda — decisão explícita do usuário, não descoberta em RF novo.

### D2 — `includeInactive` como filtro opt-in, padrão `false`

Padrão retrocompatível com o resto do sistema (isActive estrutural em todo read filter,
CLAUDE.md) — listagem só ativos por padrão; parâmetro explícito pra ver excluídos
também, sem separar em dois endpoints.

### D3 — `updated_at`: coluna nova, não derivada do histórico

`documents` só tinha `uploaded_at` (criação). Duas opções: (a) coluna `updated_at`
mantida por `@PrePersist`/`@PreUpdate` na entidade, (b) derivar de
`MAX(document_status_history.occurred_at)` por documento via subquery/join. Escolhido
(a) — mais simples, sem custo de query adicional na listagem paginada, semântica de
"linha tocada" mais direta que "última entrada de auditoria" (que são conceitos
relacionados mas não idênticos).

### D4 — Paginação via `Pageable`/`Page<T>` do Spring Data, não cursor

Idiomático pro stack já usado (`JpaRepository`), resolvido automaticamente pelo Spring
MVC (`?page=&size=&sort=`) sem wiring extra. Sem paginação por cursor/keyset — volume
esperado do projeto (caso de estudo local) não justifica a complexidade adicional.

### D5 — `DocumentQueryController` dedicado, não empilhado em controller existente

Nem `DocumentUploadController` (só aceita upload) nem `DocumentLifecycleController` (é
por-id) cabem semanticamente — listagem é responsabilidade própria, mesmo padrão de
"um config por recurso" já usado (`UploadConfig`, `DocumentLifecycleConfig`, agora
`DocumentQueryConfig`).

### D6 — Sem authority nova na Security Config

Reaproveita `.anyRequest().authenticated()` — consistente com status/history agora sendo
tenant-wide (mesma regra de autorização, sem uma role dedicada pra "quem pode listar").
