# exclusao-e-versionamento Specification

## Purpose
TBD - created by archiving change epico-2-ciclo-de-vida. Update Purpose after archive.
## Requirements
### Requirement: Exclusão lógica com isolamento de grafo

O sistema SHALL, ao comando de exclusão do dono, marcar `is_active=false` no documento (Postgres), no nó `Document` e em seus `Chunk`s (Neo4j), e inativar os vetores correspondentes (OpenSearch) — síncrono nesta versão (design.md D2). Entidades e relacionamentos conectados a chunks de **outros** documentos ativos SHALL permanecer intactos; entidade cujo único vínculo era com o documento excluído SHALL permanecer no grafo até o Garbage Collection (`garbage-collection-grafo`) remover.

#### Scenario: Exclusão mantendo integridade do grafo

- **WHEN** o "Documento_A" é excluído e sua entidade "Spring Boot" também está conectada ao "Documento_B" (outro usuário, mesmo tenant)
- **THEN** `Documento_A` e seus `Chunk`s ficam `isActive=false`, os vetores correspondentes são inativados no OpenSearch, e a entidade "Spring Boot" é preservada

#### Scenario: Vetores inativados não aparecem em busca

- **WHEN** o "Documento_A" foi excluído logicamente
- **THEN** nenhuma busca vetorial ou de grafo no tenant retorna chunk do "Documento_A" — todo filtro de recuperação considera apenas `isActive=true`

#### Scenario: Exclusão sem outras referências

- **WHEN** o "Documento_X" é o único conectado à entidade "Framework Interno" e é excluído
- **THEN** `Documento_X` e seus chunks ficam `isActive=false`, e "Framework Interno" permanece no grafo até a próxima execução do Garbage Collection

### Requirement: Substituição de versão reprocessa o pipeline

O sistema SHALL, ao substituir um documento por novo conteúdo, registrar a nova versão como `version+1` seguindo o mesmo fluxo de aceite do upload original (RECEIVED→VALIDATING→UPLOADED), e a versão anterior SHALL seguir o fluxo de exclusão lógica descrito acima.

#### Scenario: Nova versão registrada e versão anterior excluída logicamente

- **WHEN** o dono substitui "contrato.pdf" (versão 1, status `COMPLETED`) por um novo arquivo
- **THEN** a versão anterior segue o fluxo de exclusão lógica e a nova versão é registrada como versão 2, reiniciando o ciclo de vida

### Requirement: Exclusão restrita ao dono do documento

O sistema SHALL negar o comando de exclusão quando o solicitante não for o dono do documento (RF30), mantendo `is_active=true` inalterado.

#### Scenario: Usuário não pode excluir documento de outro

- **WHEN** um usuário comanda a exclusão de um documento pertencente a outro usuário do mesmo tenant
- **THEN** a operação é negada por falta de permissão e o documento permanece `is_active=true`

