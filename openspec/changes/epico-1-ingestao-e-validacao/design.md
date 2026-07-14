# Design — Épico 1: Ingestão e Validação

> O desenho de referência é `docs/sdd/ingestao.md` (§1 contratos REST, §2 cadeia de validação, §3 idempotência, §4 storage, §5 metadados/cotas) e `docs/sdd/dados.md` §2 (schemas) — **não repetido aqui**. Este documento registra apenas as decisões novas tomadas neste change e o recorte de escopo.

## Context

Épico 0 entregou: schema V1 (`documents`, `document_status_history`, `processing_errors`), app como OAuth2 Resource Server com `CallerContext`, harness E2E (Cucumber + Testcontainers + `KeycloakTokens`), JuiceFS montado. Nenhum endpoint de domínio existe. Este change implementa a entrada do sistema até o estado `UPLOADED` — sem eventos e sem pipeline, que chegam nos épicos 3–4.

## Goals / Non-Goals

**Goals:**
- `POST /api/v1/documents` completo: validação (7 etapas), storage RAW, metadados, histórico, resposta 202.
- Cenários de `ingestao/upload.feature` e `ingestao/validacao.feature` verdes (exceto reprocessamento explícito).
- Migração `V2__tenant_quotas.sql`.

**Non-Goals:**
- ClamAV real ([1.3]) — adiado por decisão de planejamento; entra a porta + mock.
- Eventos de ciclo de vida (RF12, Ép. 3 [3.1]) e desvio `QUEUED` por payload acumulado (Ép. 3 [3.3], onde vive o cenário `@ControleDeCarga`).
- Endpoints `GET status/history`, `DELETE`, `/reprocess`, `/versions` (Ép. 2).
- Qualquer escrita em OpenSearch/Neo4j.

## Decisions

| # | Decisão | Alternativa descartada | Motivo |
|---|---|---|---|
| D1 | **ClamAV adiado; `MalwareScanner` é porta no módulo `api` (internal)** com adaptador mock. Projeto de estudo: a camada real não agrega aprendizado agora; a interface preserva o ponto de extensão (trocar adaptador, zero mudança na cadeia). | Subir ClamAV no compose + Testcontainers | complexidade/RAM sem valor de estudo neste momento (decisão do usuário, jul/2026) |
| D2 | **Mock detecta apenas a assinatura EICAR** (constante padrão da indústria); resto é limpo. Permite fechar os dois cenários `@RF02` de malware validando a *cadeia* (ordem, código, não-consumo de cota), não o scanner. | Mock "sempre limpo" | deixaria o cenário EICAR `@pendente` sem necessidade — a assinatura EICAR existe exatamente para isso |
| D3 | **Novo validador de integridade estrutural (`CORRUPTED_FILE`, 400)**: PDF exige `%PDF` + `%%EOF`; imagens exigem magic bytes/estrutura mínima do formato. Estende a tabela do `sdd/ingestao.md` §2 (atualizar doc). Corrupção profunda continua indo para `EXTRACTION_FAILED` (Ép. 4). | Deixar truncado para a extração | o cenário BDD (autoridade acima do SDD) exige rejeição na validação; o check barato não contradiz o SDD, só cobre mais um caso detectável |
| D4 | **Cota é opt-in**: tenant sem linha em `tenant_quotas` = sem limite; BDD insere a linha no `Given`. | Default global em config; seed obrigatório | zero superfície de config; CRUD admin de cotas não tem RF/épico ainda |
| D5 | **`rag` é dono da persistência**: entidade/repos de `documents` + gravação de histórico ficam no `rag` (fatia mínima do futuro `DocumentLifecycleService`); `api` consome a API pública `DocumentCommandApi` (registro do aceite) e consultas de apoio à validação (duplicidade, uso de cota) — dependência `api → rag` já permitida. | `api` persistir direto | SDD: só o lifecycle (módulo `rag`) transiciona status; evita mover tabelas de módulo no Ép. 2 |
| D6 | **Sem eventos neste change**: o aceite grava as transições `RECEIVED → VALIDATING → UPLOADED` de uma vez no histórico (conforme `sdd/ingestao.md` §2) e o documento permanece `UPLOADED`. O `202` responde `status: "UPLOADED"`; o cenário RF01 ("status inicial RECEIVED") é validado pela **primeira linha do histórico**, não pelo status corrente. | Publicar `DocumentUploadedEvent` já | RF12 é do Ép. 3; evento sem consumidor é ruído no registry |
| D7 | **Duplicidade neste change**: `UPLOADED` conta como "sucesso anterior" (é o estado final alcançável sem pipeline); quando os estados terminais reais existirem, a regra do `sdd/ingestao.md` §3 (`COMPLETED`/`PARTIALLY_COMPLETED`/em processamento) passa a valer sem mudança de código — a checagem é "status ∉ {FAILED}". | Hardcode da lista final | lista final não é alcançável ainda; a forma negada (`≠ FAILED`) já é a semântica correta dos dois mundos |
| D8 | **`DocumentStorage` (porta no `shared`, ADR-001) com adaptador filesystem POSIX** de diretório-base configurável (`app.storage.base-dir`): dev aponta para o mount JuiceFS (`./tmp/blobstore`), BDD aponta para diretório temporário. | Adaptador S3/MinIO direto | ADR-001 escolheu POSIX via JuiceFS exatamente para o adaptador ser filesystem simples |
| D9 | **Hash em streaming**: uma passada — `DigestInputStream` → arquivo temporário → validações que precisam de conteúdo → storage. Nada de carregar o multipart em memória. | `byte[]` em memória | arquivos de 5MB × uploads concorrentes; o SDD §3 já pede streaming |

## Risks / Trade-offs

- [Mock de malware não valida integração clamd] → risco aceito e documentado; o débito fica registrado no proposal e a troca é 1 adaptador. Quando o ClamAV entrar, os cenários BDD não mudam.
- [Check estrutural gera falso negativo (PDF com `%%EOF` mas quebrado)] → por design: caso profundo pertence a `EXTRACTION_FAILED` (RF27); o validador só cobre o barato.
- [`UPLOADED` eterno até o Ép. 3/4 (sem pipeline)] → esperado nesta fase; status/history do Ép. 2 tornarão isso visível ao usuário.
- [Validação de duplicidade/cota tem corrida (2 uploads simultâneos do mesmo hash)] → a constraint `UNIQUE (tenant, owner, hash, version)` é o guarda final; a violação vira `DUPLICATE_FILE` no advice. Cota tem corrida análoga sem constraint — aceitável em ambiente de estudo, anotar no código.
- [Validação exige leitura do arquivo várias vezes (Tika, integridade, EICAR, hash)] → mitigado por D9: uma gravação em arquivo temporário; validadores leem do temp, não do multipart.

## Migration Plan

1. `V2__tenant_quotas.sql` (aditiva, banco sem dado real — sem rollback especial).
2. Código novo é endpoint inédito — sem breaking em API existente.
3. Docs: atualizar `docs/sdd/ingestao.md` §2 (CORRUPTED_FILE + ClamAV adiado/mock) e `docs/rag-plan.md` §5/[1.3] ao concluir.

## Open Questions

Nenhuma — dúvidas de planejamento resolvidas com o usuário em jul/2026 (ClamAV fora, check estrutural, QUEUED deferido, cota opt-in).
