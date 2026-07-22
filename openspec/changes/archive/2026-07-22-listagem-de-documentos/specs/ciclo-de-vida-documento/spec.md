## MODIFIED Requirements

> RF09 revisado (`docs/requisitos.md`). Motivo: a listagem tenant-wide (`listagem-de-documentos`) só faz sentido se o usuário conseguir depois abrir o detalhe de um documento de outro dono que apareceu na lista — decisão confirmada com o usuário em 2026-07-22 (`design.md` D1). Critérios de aceite executáveis: `src/test/resources/features/ciclo-de-vida/status-e-historico.feature`.

### Requirement: Consulta de status atual e histórico completo

O sistema SHALL expor consulta do status atual e do histórico completo de transições
(etapa, status resultante, timestamp, em ordem cronológica) de um documento (RF09). A
leitura SHALL ser compartilhada entre todos os usuários autenticados do mesmo tenant —
não restrita ao dono do documento. Documento inexistente ou de outro tenant SHALL
responder erro de "não encontrado" limpo, sem expor detalhe do motivo real da negativa.
Documento excluído logicamente (`isActive=false`) SHALL responder "não encontrado" para
consulta de **status** (não existe "estado atual" de algo removido), mas o **histórico**
SHALL permanecer acessível, incluindo o próprio evento de exclusão — auditoria (RF31)
sobrevive à exclusão lógica.

#### Scenario: Consulta do status atual

- **WHEN** o usuário consulta o status de um documento do seu tenant, atualmente em `EXTRACTING`
- **THEN** a resposta informa o status atual `EXTRACTING`

#### Scenario: Consulta do histórico completo

- **WHEN** o usuário consulta o histórico de um documento concluído
- **THEN** a resposta lista todas as etapas executadas em ordem cronológica, cada uma com etapa, status resultante e timestamp

#### Scenario: Consulta de documento inexistente

- **WHEN** o usuário consulta o status de um id que não existe
- **THEN** a resposta é rejeitada com "Documento não encontrado"

#### Scenario: Consulta de histórico de documento de outro usuário do mesmo tenant é permitida

- **WHEN** o usuário consulta o histórico de um documento de outro dono, mesmo tenant
- **THEN** a consulta é aceita normalmente

#### Scenario: Consulta de histórico de documento de outro tenant retorna não encontrado

- **WHEN** o usuário consulta o histórico de um documento de um tenant diferente
- **THEN** a resposta é rejeitada com "Documento não encontrado"

#### Scenario: Histórico continua acessível após exclusão lógica, com o evento registrado

- **WHEN** o usuário consulta o histórico de um documento que foi excluído logicamente
- **THEN** a consulta é aceita e a última entrada do histórico tem status `DELETED` e detail "Documento excluído logicamente"

#### Scenario: Status de documento excluído logicamente retorna não encontrado

- **WHEN** o usuário consulta o status de um documento que foi excluído logicamente
- **THEN** a resposta é rejeitada com "Documento não encontrado", igual a um id inexistente
