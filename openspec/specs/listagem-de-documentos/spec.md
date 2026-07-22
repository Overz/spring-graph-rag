# listagem-de-documentos Specification

## Purpose
Listar documentos de um tenant de forma paginada e compartilhada entre todos os
usuários autenticados do tenant (RF40) — complementa a consulta por id (RF09,
`ciclo-de-vida-documento`) permitindo descobrir quais documentos existem sem já saber
os ids de antemão.

## Requirements
### Requirement: Listagem paginada e compartilhada no tenant

O sistema SHALL expor uma listagem paginada dos documentos de um tenant, visível para
qualquer usuário autenticado do tenant — não restrita ao dono de cada documento. Por
padrão SHALL trazer apenas documentos ativos (`isActive=true`); um parâmetro opcional
`includeInactive` (padrão `false`) SHALL incluir também os excluídos logicamente. Nomes
de arquivo duplicados entre documentos distintos SHALL ser permitidos, sem deduplicação.
Cada item SHALL trazer, no mínimo: identificador, nome do arquivo, status atual, uma
flag booleana de ativo/inativo, quem enviou (`ownerId`), versão, data de criação e data
da última atualização. Documento excluído logicamente SHALL reportar status `DELETED`
(estado terminal persistido pela exclusão, não um estágio de pipeline) e a flag de
ativo em `false`. A listagem SHALL NOT incluir documentos de outro tenant.

#### Scenario: Listagem traz apenas documentos ativos por padrão

- **WHEN** o tenant tem um documento ativo e um documento excluído logicamente
- **THEN** a listagem contém o documento ativo e não contém o excluído

#### Scenario: Listagem com includeInactive traz também documentos excluídos

- **WHEN** o usuário lista com `includeInactive=true`
- **THEN** a listagem contém tanto o documento ativo quanto o excluído logicamente, o
  item excluído com status `DELETED` e a flag de ativo em `false`, o item ativo com seu
  status real e a flag de ativo em `true`

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
