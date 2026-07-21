# ciclo-de-vida-documento Specification

## Purpose
Rastrear o ciclo de vida de processamento de um documento (RF08 fork-join) e expor
consulta de status/histórico completo (RF09) — a base sobre a qual RF10/RF11
(exclusão, versionamento, garbage collection) e os épicos 3–6 (pipeline real)
se apoiam.
## Requirements
### Requirement: Máquina de estados com fork-join independente

O sistema SHALL transitar o documento pelos status `RECEIVED → VALIDATING → UPLOADED → QUEUED → EXTRACTING → TRANSFORMING → CHUNKING`, e a partir de `CHUNKING` SHALL disparar os ramos `EMBEDDING` e `GRAPH_BUILDING` com sub-estados independentes (`embeddingStatus`, `graphStatus`). O status agregado SHALL ser derivado dos dois sub-estados: ambos concluídos → `COMPLETED`; um concluído e o outro falhado definitivamente → `PARTIALLY_COMPLETED`; ambos falhados definitivamente → `FAILED`. Falha em um ramo SHALL NOT afetar o outro.

#### Scenario: Sub-estados independentes dos ramos paralelos

- **WHEN** o documento concluiu `CHUNKING` e o ramo `EMBEDDING` termina enquanto `GRAPH_BUILDING` ainda executa
- **THEN** `embeddingStatus` é `COMPLETED` e `graphStatus` é `RUNNING`, e o status geral é derivado dos dois

#### Scenario: Sucesso em um ramo com falha definitiva no outro

- **WHEN** o ramo `EMBEDDING` conclui com sucesso e o ramo `GRAPH_BUILDING` falha definitivamente após esgotar retries
- **THEN** o documento é marcado `PARTIALLY_COMPLETED` e os chunks permanecem disponíveis para busca vetorial (RF25)

#### Scenario: Falha definitiva em ambos os ramos

- **WHEN** `EMBEDDING` e `GRAPH_BUILDING` falham definitivamente
- **THEN** o documento é marcado `FAILED`

### Requirement: Consulta de status atual e histórico completo

O sistema SHALL expor consulta do status atual e do histórico completo de transições (etapa, status resultante, timestamp, em ordem cronológica) de um documento (RF09). Documento inexistente, de outro tenant/dono, ou já excluído logicamente (`isActive=false`) SHALL responder erro de "não encontrado" limpo, sem expor detalhe do motivo real da negativa.

#### Scenario: Consulta do status atual

- **WHEN** o usuário consulta o status de um documento seu, atualmente em `EXTRACTING`
- **THEN** a resposta informa o status atual `EXTRACTING`

#### Scenario: Consulta do histórico completo

- **WHEN** o usuário consulta o histórico de um documento concluído
- **THEN** a resposta lista todas as etapas executadas em ordem cronológica, cada uma com etapa, status resultante e timestamp

#### Scenario: Consulta de documento inexistente

- **WHEN** o usuário consulta o status de um id que não existe
- **THEN** a resposta é rejeitada com "Documento não encontrado"

#### Scenario: Consulta de documento excluído logicamente

- **WHEN** o usuário consulta o status ou o histórico de um documento que ele mesmo excluiu (`exclusao-e-versionamento`)
- **THEN** a resposta é rejeitada com "Documento não encontrado", igual a um id inexistente

