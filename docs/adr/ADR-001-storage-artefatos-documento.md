# ADR-001: Armazenamento de Artefatos de Documento — Cold Storage por Estágio, Não Texto em Banco Relacional

## Status
Aceito

## Contexto

O pipeline de ingestão do RAG (ver `docs/rag-plan.md`, Épicos 1–6) produz, para cada documento enviado, uma sequência de artefatos de tamanhos e naturezas diferentes:

1. **Bruto** — os bytes originais do upload (PDF, DOCX, TXT, MD, HTML, CSV).
2. **Transformado** — o texto puro extraído pelo parser (Tika/PDF reader), antes de qualquer limpeza.
3. **Sanitizado** — o texto pós-limpeza (remoção de ruído estrutural, mascaramento de PII), que alimenta o chunking.
4. **Chunks + embeddings** — produzidos a partir do texto sanitizado, indexados no OpenSearch.

A v1 do `rag-plan.md` (Épico 1.4) previa gravar o arquivo bruto direto via SDK S3/MinIO, e o Épico 2.4 previa persistir o texto extraído "em uma tabela/coluna de staging" no Postgres, para fins de auditoria — comparar "texto extraído" vs. "texto final" de qualquer documento. Essa segunda decisão nunca chegou a aparecer no schema da seção 6.1 do plano (que só tinha um `storage_path` genérico), e foi revisitada numa sessão de arquitetura que levantou duas perguntas:

- **Por que a app grava num diretório e vira objeto S3 de verdade?** Já resolvido antes desta ADR: adotamos JuiceFS (metadados no Redis, dados no MinIO) montado via FUSE em `./tmp/blobstore`, propagado do container pro host — a app escreve arquivo comum, o JuiceFS traduz pra objeto S3 por baixo. Ver `compose.yaml` (serviços `minio`, `redis`, `juicefs-format`, `juicefs-mount`).
- **Por que texto extraído/sanitizado iria para o Postgres, e não para o mesmo cold storage do arquivo bruto?** Esta é a pergunta que esta ADR resolve.

O argumento inicial a favor do Postgres foi "consultabilidade" — mas ao examinar quem de fato consultaria esse texto, ficou claro que **o caminho de resposta do RAG (pergunta → busca no OpenSearch → prompt → LLM) nunca lê o texto completo de um documento**. Ele só lê chunks, que já vivem no OpenSearch com vida própria. Os únicos consumidores do texto completo são humanos debugando o pipeline e jobs de reprocessamento em lote — nenhum dos dois exige que o conteúdo esteja dentro de uma linha do Postgres, só que exista uma referência confiável para encontrá-lo.

Também ficou definido, como decisão de produto explícita, que **os artefatos não serão apagados** — funcionam como cold storage permanente (trilha de auditoria), então o custo de "duplicar" dado entre arquivo e banco não é uma preocupação de espaço; a preocupação real é outra: onde cada tipo de dado se encaixa melhor dado o seu padrão de uso.

## Decisão

1. **Um único port (`DocumentStorage`) para todo artefato de documento**, não só o arquivo bruto. Vive no módulo `shared` (contrato cruzado entre `api`, que recebe o upload, e `rag`, que processa):

   ```java
   public interface DocumentStorage {
     StorageKey store(DocumentStage stage, String filename, InputStream content);
     InputStream retrieve(StorageKey key);
     void delete(StorageKey key);
   }
   ```

2. **Vocabulário fechado de estágio** (`DocumentStage`: `RAW`, `TRANSFORMED`, `SANITIZED`) — mesmo espírito do vocabulário fechado de relações do Épico 17.2: evita que o mesmo conceito acabe gravado sob chaves/nomes diferentes ao longo do tempo.

3. **A implementação atual (`FilesystemDocumentStorage`) grava em filesystem** — hoje, o mount JuiceFS descrito acima; uma subpasta por estágio (`raw/`, `transformed/`, `sanitized/`) sob o mesmo base path. Trocar para um cliente S3 direto no futuro não deve exigir mudança em quem consome a interface.

4. **O Postgres nunca guarda o conteúdo do texto** — só:
   - `status` do pipeline (`PENDING → PARSING → ...`);
   - a **referência** (`StorageKey`, uma string opaca) para cada estágio já persistido;
   - metadado estruturado de verdade (tags, categoria, idioma, confidencialidade, versão — Épico 4.1);
   - estatística derivada leve, quando existir necessidade objetiva de checagem sem reabrir o arquivo (ex.: `extracted_text_length`, usado pela validação de conteúdo vazio do Épico 3.4).

5. **Chunking não produz artefato próprio no `DocumentStorage`** — chunk + embedding vão direto pro OpenSearch a partir do texto `SANITIZED` já em cold storage; não há um quarto estágio de arquivo.

## Alternativas Consideradas

- **Texto completo em coluna do Postgres** (redação original do Épico 2.4): rejeitada. Nenhum consumidor do caminho de resposta do RAG lê esse texto; os dois consumidores reais (debug humano, reprocessamento em lote) são igualmente bem servidos por uma referência + arquivo em cold storage, sem inchar linhas/backups do banco com blobs grandes raramente lidos.
- **Cliente S3/MinIO SDK direto, sem camada de filesystem**: rejeitada para o objetivo deste projeto de simular armazenamento de blob "montado" (o mesmo padrão de um CSI driver de blobstorage num pod k8s) — e também dificultaria inspeção direta dos artefatos durante o desenvolvimento.
- **s3fs-fuse em vez de JuiceFS**: rejeitada — s3fs emula listagem de diretório e não garante rename atômico; JuiceFS tem metadados reais (Redis) e semântica POSIX de verdade. Não é escolha desta ADR especificamente, mas é o alicerce sobre o qual ela se apoia.
- **Artefatos efêmeros (apagar após cada estágio avançar)**: rejeitada por decisão de produto — os artefatos ficam retidos indefinidamente como trilha de auditoria.

## Consequências

### Positivas
- **Atomicidade sem duplicar dado grande**: status + referência (strings pequenas) cabem numa única transação do Postgres; o conteúdo pesado fica fora da transação, sem risco de blob parcialmente escrito no meio de um commit.
- **Postgres e backups continuam magros** — crescem com o número de documentos, não com o tamanho do texto de cada um.
- **Debug direto por arquivo** — abrir `./tmp/blobstore/transformed/<key>` com qualquer ferramenta, sem precisar de SQL.
- **Backend de storage trocável** sem tocar em quem consome `DocumentStorage`.
- **Detecção de extração vazia (3.4) sem I/O de arquivo** — é só comparar `extracted_text_length` na própria linha.

### Negativas
- **Ler o conteúdo de fato custa um salto extra** (ida ao cold storage) comparado a uma coluna inline — aceitável porque isso nunca acontece no caminho de resposta do RAG, só em debug/reprocessamento.
- **Ordem de escrita importa**: é preciso gravar o arquivo com sucesso *antes* de persistir a `StorageKey` no Postgres — nunca o inverso. Um arquivo gravado e nunca referenciado é inofensivo (a política já é reter tudo mesmo); uma referência apontando para um arquivo que não existe seria um bug real e silencioso.
- **Mais uma peça em dev local** (FUSE via JuiceFS) — mitigado: documentado no `compose.yaml`, e depende de uma pré-condição do host (`/` propagado como `shared`) que já é o padrão em distros systemd modernas.

## Plano de Ação

1. ~~Definir `DocumentStorage` / `DocumentStage` / `FilesystemDocumentStorage` no módulo `shared`.~~ **Feito.**
2. ~~Provisionar MinIO + Redis + JuiceFS (format/mount) no `compose.yaml`, mount em `./tmp/blobstore`.~~ **Feito.**
3. Estender a tabela `documents` (Flyway, Épico 1.5) com `raw_storage_key`, `transformed_storage_key`, `sanitized_storage_key` e `extracted_text_length`, substituindo o `storage_path` genérico da v1 do plano.
4. Fazer o pipeline de ingestão (Épicos 2–3) efetivamente chamar `DocumentStorage.store(...)` em cada estágio e persistir a referência retornada.
5. Reavaliar esta ADR se surgir um consumidor real que precise consultar texto completo via SQL — hoje nenhum existe.
