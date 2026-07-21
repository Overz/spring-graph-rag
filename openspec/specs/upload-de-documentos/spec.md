# upload-de-documentos Specification

## Purpose

RF01, RF03, RF05, RF06 (`docs/requisitos.md`): upload autenticado de documentos, resposta assíncrona, armazenamento segregado por tenant/usuário, metadados/histórico persistidos e limites de tamanho e cota. Validada por `src/test/resources/features/ingestao/upload.feature`. Design: `docs/sdd/ingestao.md` §§1, 4, 5.

## Requirements

### Requirement: Upload autenticado com resposta assíncrona
O sistema SHALL expor `POST /api/v1/documents` (multipart, campo `file`) respondendo `202 Accepted` com `{id, status, correlationId, version}`. A rota SHALL exigir JWT válido com a role `document:upload`; `tenantId`/`ownerId` SHALL sair exclusivamente do `CallerContext` (claims), nunca do corpo. Um `correlationId` único SHALL ser gerado no ato do upload (RF01, RF28 complemento).

#### Scenario: Upload bem-sucedido de arquivo suportado (@RF01)
- **WHEN** usuário autenticado envia arquivo suportado dentro do limite
- **THEN** upload aceito com identificador único; primeira transição registrada é `RECEIVED` (ver `upload.feature`)

### Requirement: Armazenamento do original segregado por tenant e usuário
O sistema SHALL gravar o arquivo original via porta `DocumentStorage` (ADR-001) na chave `/{tenantId}/{userId}/raw/{fileId}/{filename}`, com conteúdo byte a byte idêntico ao enviado. A gravação no storage SHALL preceder a persistência da linha em `documents`; o Postgres guarda a chave retornada pela porta, nunca caminho de filesystem cru (RF05).

#### Scenario: Original salvo no caminho segregado (@RF05)
- **WHEN** upload de "contrato.pdf" é aceito com id "doc-123" para `acme_inc`/`dev_user`
- **THEN** arquivo existe em `/acme_inc/dev_user/raw/doc-123/contrato.pdf`, idêntico ao enviado (ver `upload.feature`)

### Requirement: Metadados e histórico persistidos
O sistema SHALL persistir em `documents` (schema V1) todos os campos do RF06: id, `owner_id`, `tenant_id`, nome original, extensão, tamanho, hash SHA-256, data de envio, chave no storage, `version = 1` e status — e SHALL registrar em `document_status_history` as transições `RECEIVED → VALIDATING → UPLOADED` do aceite (RF06, RF08 parcial).

#### Scenario: Metadados completos na base relacional (@RF06)
- **WHEN** upload de "notas.md" (10KB) é aceito
- **THEN** linha em `documents` contém todos os campos da tabela do cenário, com hash SHA-256 do conteúdo e versão 1 (ver `upload.feature`)

### Requirement: Limite de tamanho por arquivo individual
O sistema SHALL rejeitar arquivos acima de **5MB** com `413` e `code = FILE_TOO_LARGE`, informando o motivo e orientando a pré-divisão de documentos grandes (RF03 + complemento do RF15). Arquivos de exatamente 5MB SHALL ser aceitos. O limite multipart do container fica em 6MB para que a resposta seja o `ProblemDetail` de domínio ([0.5], Ép. 0).

#### Scenario: Limite aplicado nas fronteiras (@RF03)
- **WHEN** uploads de 512KB, 5MB, 6MB e 50MB são enviados
- **THEN** os dois primeiros são aceitos e os dois últimos rejeitados com motivo e orientação de pré-divisão (ver `upload.feature`)

### Requirement: Cota de armazenamento por tenant
O sistema SHALL validar a cota do tenant (tabela `tenant_quotas`: `max_storage_bytes`, `max_active_files`) antes de aceitar o upload, considerando o uso corrente derivado dos documentos `is_active = true` do tenant; excedida, SHALL rejeitar com `422` e `code = QUOTA_EXCEEDED`, sem publicar evento algum. Tenant **sem linha** em `tenant_quotas` SHALL não ter limite (cota opt-in — decisão deste change) (RF03 complemento).

#### Scenario: Cota de armazenamento esgotada (@RF03)
- **WHEN** tenant com cota 1GB e 1023MB ocupados envia arquivo de 2MB
- **THEN** upload rejeitado com motivo de cota excedida e nenhum evento publicado (ver `upload.feature`)
