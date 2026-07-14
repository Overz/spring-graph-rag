# Spec — validacao-de-ingestao

> RF02 (+ complemento de malware), RF04, RF07 (`docs/requisitos.md`). Critérios de aceite executáveis: `src/test/resources/features/ingestao/validacao.feature`. Design: `docs/sdd/ingestao.md` §§2–3.

## ADDED Requirements

### Requirement: Cadeia de validação ordenada com códigos estáveis
O sistema SHALL executar as validações na ordem do mais barato ao mais caro — tamanho → nome → vazio → integridade estrutural → extensão × MIME real → duplicidade → cota → malware — interrompendo na primeira falha. Cada rejeição SHALL responder `ProblemDetail` (RFC 9457) via `HttpApplicationError` com `code` estável. Rejeição SHALL NOT criar linha em `documents`, gravar no storage nem publicar evento; a tentativa vai para log estruturado (RF02).

#### Scenario: Falha interrompe sem efeitos colaterais
- **WHEN** qualquer validação rejeita o arquivo
- **THEN** nada é salvo em `/raw` nem em `documents` e nenhum evento do ciclo de vida é publicado (asserções recorrentes em `validacao.feature` e `upload.feature`)

### Requirement: Tipos de arquivo suportados
O sistema SHALL aceitar exclusivamente PDF, JPG, JPEG, PNG, CSV, JSON, XML, TXT e Markdown (RF04) e rejeitar qualquer outro formato com `415` e `code = UNSUPPORTED_FILE_TYPE`.

#### Scenario: Matriz de aceitação e rejeição (@RF04)
- **WHEN** enviados os 9 tipos suportados e formatos como mp4, mp3, exe, xlsx, sh
- **THEN** os suportados prosseguem para `UPLOADED`; os demais são rejeitados com "Tipo MIME não suportado" (ver `validacao.feature`)

### Requirement: MIME real detectado por conteúdo
O sistema SHALL detectar o tipo MIME pelo **conteúdo** (Tika), nunca pela extensão; extensão e MIME real SHALL ambos pertencer à lista do RF04 e ser coerentes entre si — divergência rejeita com `code = MIME_MISMATCH` ou `UNSUPPORTED_FILE_TYPE` (RF02).

#### Scenario: Executável disfarçado de PDF (@RF02)
- **WHEN** "fatura.pdf" contém conteúdo `application/x-msdownload`
- **THEN** rejeitado pelo MIME real, não pela extensão (ver `validacao.feature`)

### Requirement: Validação de nome de arquivo
O sistema SHALL rejeitar com `400` e `code = INVALID_FILENAME` nomes vazios, acima de 255 caracteres, com caracteres de controle ou com padrões de path traversal (`..`, separadores de caminho) (RF02).

#### Scenario: Path traversal no nome (@RF02)
- **WHEN** upload de "../../etc/senhas.pdf"
- **THEN** rejeitado com "Nome de arquivo inválido" (ver `validacao.feature`)

### Requirement: Rejeição de arquivo vazio
O sistema SHALL rejeitar arquivos de 0 bytes com `400` e `code = EMPTY_FILE` (RF02).

#### Scenario: Arquivo de 0KB (@RF02)
- **WHEN** upload de "vazio.pdf" com 0KB
- **THEN** rejeitado com "Arquivo vazio" (ver `validacao.feature`)

### Requirement: Integridade estrutural barata
O sistema SHALL executar verificação estrutural leve por formato (decisão deste change, estendendo o `sdd/ingestao.md` §2): PDF exige assinatura `%PDF` e trailer `%%EOF`; imagens exigem header/footer válidos do formato. Falha rejeita com `400` e `code = CORRUPTED_FILE`. Corrupção profunda que passe neste check continua sendo responsabilidade da etapa de extração (`EXTRACTION_FAILED`, RF27, Ép. 4) (RF02).

#### Scenario: PDF truncado (@RF02)
- **WHEN** upload de "corrompido.pdf" com conteúdo truncado e ilegível
- **THEN** rejeitado com "Arquivo corrompido" (ver `validacao.feature`)

### Requirement: Varredura de malware atrás de porta
A cadeia SHALL submeter o arquivo à porta `MalwareScanner` como última validação; detecção rejeita com `422` e `code = MALWARE_DETECTED`, sem consumir cota de reprocessamento e sem gravar em `/raw` (RF02 complemento). Neste change a implementação é **mock determinístico**: detecta apenas a assinatura de teste EICAR, qualquer outro conteúdo é limpo — a integração ClamAV real (clamd) é débito registrado para épico futuro, trocando somente o adaptador.

#### Scenario: Assinatura EICAR detectada (@RF02)
- **WHEN** upload de "boleto.pdf" contendo a assinatura EICAR
- **THEN** rejeitado com `MALWARE_DETECTED`, sem consumo de cota nem gravação em `/raw` (ver `validacao.feature`)

#### Scenario: Arquivo limpo liberado (@RF02)
- **WHEN** upload de arquivo sem ameaças passa pela varredura
- **THEN** documento prossegue para `UPLOADED` (ver `validacao.feature`)

### Requirement: Idempotência por hash SHA-256
O sistema SHALL calcular SHA-256 em streaming durante o upload e rejeitar com `409` e `code = DUPLICATE_FILE` quando já existir documento do **mesmo tenant+owner** com o mesmo hash em status de sucesso (`COMPLETED`/`PARTIALLY_COMPLETED` — neste change, `UPLOADED` conta como sucesso por ser o estado final alcançável) ou em processamento. O mesmo hash de **outro usuário** SHALL NOT bloquear; documento anterior `FAILED` SHALL NOT bloquear (RF07). O bypass por comando explícito de reprocessamento existe no modelo (chave única com `version`), mas o endpoint `/reprocess` é de épico futuro — seu cenário permanece `@pendente`.

#### Scenario: Duplicata do mesmo dono rejeitada (@RF07)
- **WHEN** "arquitetura.pdf" já enviado com sucesso é reenviado pelo mesmo usuário
- **THEN** rejeitado na validação, sem novo evento do ciclo de vida (ver `validacao.feature`)

#### Scenario: Escopo por dono e falha anterior não bloqueiam (@RF07)
- **WHEN** outro usuário do tenant envia o mesmo conteúdo, ou o dono reenvia arquivo cujo processamento anterior terminou `FAILED`
- **THEN** ambos os uploads são aceitos (ver `validacao.feature`)
