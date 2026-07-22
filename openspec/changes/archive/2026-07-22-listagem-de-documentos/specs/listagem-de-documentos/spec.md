## ADDED Requirements

> RF40 (`docs/requisitos.md`). Critérios de aceite executáveis: `src/test/resources/features/ciclo-de-vida/listagem-de-documentos.feature`. Design: `design.md` D1-D6.

### Requirement: Listagem paginada e compartilhada no tenant

O sistema SHALL expor uma listagem paginada dos documentos de um tenant, visível para
qualquer usuário autenticado do tenant — não restrita ao dono de cada documento. Por
padrão SHALL trazer apenas documentos ativos (`isActive=true`); um parâmetro opcional
`includeInactive` (padrão `false`) SHALL incluir também os excluídos logicamente. Nomes
de arquivo duplicados entre documentos distintos SHALL ser permitidos, sem deduplicação.
Cada item SHALL trazer, no mínimo: identificador, nome do arquivo, status atual, quem
enviou (`ownerId`), versão, data de criação e data da última atualização. A listagem
SHALL NOT incluir documentos de outro tenant.

#### Scenario: Listagem traz apenas documentos ativos por padrão

- **WHEN** o tenant tem um documento ativo e um documento excluído logicamente
- **THEN** a listagem contém o documento ativo e não contém o excluído

#### Scenario: Listagem com includeInactive traz também documentos excluídos

- **WHEN** o usuário lista com `includeInactive=true`
- **THEN** a listagem contém tanto o documento ativo quanto o excluído logicamente

#### Scenario: Listagem é compartilhada entre usuários do mesmo tenant

- **WHEN** dois usuários distintos do mesmo tenant enviaram documentos diferentes
- **THEN** a listagem de qualquer um dos dois contém os documentos de ambos, cada item
  informando quem enviou

#### Scenario: Listagem nunca inclui documentos de outro tenant

- **WHEN** um documento pertence a um tenant diferente do chamador
- **THEN** a listagem do chamador não contém esse documento

#### Scenario: Listagem é paginada

- **WHEN** o tenant tem mais documentos do que o tamanho de página solicitado
- **THEN** a página retornada contém exatamente o tamanho solicitado e há mais de uma
  página de resultado
