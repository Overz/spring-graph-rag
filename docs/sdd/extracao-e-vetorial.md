# Extração, Transformação e Pipeline Vetorial

> Parte do [SDD](../sdd.md). Cobre as etapas `EXTRACTING → TRANSFORMING → CHUNKING → EMBEDDING` (RF14–RF20).
> **Features BDD:** `extracao/extracao-e-transformacao.feature`, `pipeline-vetorial/chunking.feature`, `pipeline-vetorial/embeddings.feature`.

---

## 1. Delegação por MIME (RF14)

`DocumentReaderFactory` (interno ao `rag`) resolve o leitor pelo MIME **real** persistido no upload (detectado por Tika — nunca pela extensão):

| MIME | Leitor | Estratégia |
|---|---|---|
| `application/pdf`, `image/jpeg`, `image/png` | `DoclingReader` | HTTP para o docling-serve (ADR-002): layout, tabelas, OCR automático quando não há camada de texto (RF15). Saída já em Markdown |
| `text/markdown`, `text/plain` | `TextReader` | Leitura direta; TXT vira Markdown por normalização (§3) |
| `text/csv` | `CsvReader` | Preserva linhas + cabeçalho; chunking próprio (§4.3) |
| `application/json` | `JsonReader` | Preserva estrutura; chunking próprio (§4.3) |
| `application/xml`, `text/xml` | `XmlReader` | Extração de texto estrutural (Tika como apoio) |

Contrato do leitor: `read(storageKey) → ExtractedContent` (conteúdo + metadados nativos: páginas, idioma detectado, presença de OCR). MIME sem leitor registrado é impossível por construção — a validação do upload (RF04) só deixa entrar o que a factory suporta; se acontecer (dado legado), falha como `EXTRACTION_FAILED` com código específico.

## 2. Extração e OCR (RF15)

- Docling processa PDF nativo e escaneado com o mesmo pipeline — decide OCR por página, conforme a presença de camada textual. JPG/PNG são sempre OCR.
- Chamada síncrona do ponto de vista da etapa (o listener aguarda), com timeout generoso (default 120s — documentos escaneados multipágina são lentos em CPU) e circuit breaker próprio (`resiliencia-e-operacao.md`).
- **Artefato intermediário persistido:** a saída da extração é gravada via `DocumentStorage.store(EXTRACTED, ...)` antes de publicar `DocumentExtractedEvent`. Motivo: RF27 — se `TRANSFORMING` falhar, o retry **não pode** repetir OCR (a etapa mais cara do pipeline). Isso adiciona o estágio `EXTRACTED` à porta da ADR-001 (RAW/EXTRACTED/TRANSFORMED).

## 3. Normalização para Markdown (RF16, RF17)

Toda saída converge para **Markdown padronizado**:

- Docling já emite Markdown (títulos, tabelas, listas preservados); imagens viram placeholders com texto alternativo quando houver.
- TXT: parágrafos preservados, sem inferência de estrutura. CSV/JSON/XML: conteúdo íntegro em blocos cercados (```` ```csv ```` etc.) — a estrutura original é o valor; convertê-los em prosa/tabela Markdown perderia fidelidade.
- Normalizações comuns: line endings `\n`, colapso de espaço redundante, remoção de caracteres de controle, headings normalizados (`#` hierárquico).
- Resultado gravado em `/{tenantId}/{userId}/transformed/{fileId}/{version}.md` (RF17); a chave vai para `documents.transformed_storage_key` e o status transiciona via `DocumentLifecycleService`.

## 4. Chunking hierárquico (RF18)

### 4.1 Estrutura

- **Chunk pai** — alvo 1500–2000 tokens: seção do documento (delimitada por headings); seções pequenas adjacentes são fundidas, seções gigantes são divididas em fronteira de parágrafo. É o que vai no prompt da geração (RF26).
- **Chunk filho** — alvo 300–500 tokens: divisão do pai em fronteira de parágrafo/sentença. É a unidade de embedding e indexação (RF19/RF20).
- Cada chunk: `chunkId` determinístico (UUIDv5 de `documentId:version:posiçãoPai[:posiçãoFilho]` — reprocessar gera os mesmos ids, tornando a indexação um upsert idempotente), conteúdo, posição ordinal, offsets no Markdown, `documentId`, `version`.
- **Persistência:** os chunks (pais e filhos, com conteúdo) vivem numa tabela `chunks` do Postgres — é o repositório autoritativo que o retry do `EMBEDDING` reaproveita (cenário BDD de recuperação parcial: "aproveitando os chunks já salvos no banco") e de onde a geração busca o conteúdo dos pais. OpenSearch indexa **só os filhos** (busca); Neo4j registra a topologia (`CHILD_OF`) sem conteúdo.

### 4.2 Contagem de tokens (default técnico)

`TokenCounter` (porta interna) implementado com **jtokkit** (BPE `cl100k_base`) como aproximação. Justificativa: os alvos do RF18 são faixas, não limites duros; uma aproximação *consistente* basta para calibrá-las. Alternativas descartadas: tokenizer exato do modelo via API do Ollama (uma chamada HTTP por medição — inviável no chunking) e DJL/HuggingFace tokenizers (dependência pesada para ganho marginal). Se a medição do golden set indicar chunks sistematicamente desalinhados, a porta troca de implementação sem tocar o chunker.

### 4.3 Conteúdo sem estrutura semântica (CSV/JSON)

- **CSV:** chunks de tamanho fixo por grupos de linhas dentro do orçamento de tokens, com **cabeçalho repetido em todo chunk** (um chunk de CSV sem cabeçalho é ininteligível para embedding e para a LLM) e overlap de 10–15% em linhas.
- **JSON:** array no topo → divisão por elementos; objeto → janelas de tamanho fixo com overlap de 10–15%, respeitando fronteiras de chave quando possível.
- Pai/filho também se aplica: o pai é o grupo maior de linhas/elementos; filhos são subdivisões.

## 5. Embeddings (RF19)

- Disparado por `ChunksReadyForEmbeddingEvent` (ramo vetorial do fork). `EmbeddingModel` do Spring AI → Ollama → `nomic-embed-text` (768d, ADR-003).
- **Prefixos de tarefa obrigatórios**: `nomic-embed-text` é treinado com prefixos — documentos são embedados como `search_document: {texto}` e consultas como `search_query: {pergunta}`. Omitir os prefixos degrada o retrieval silenciosamente; a regra vive num único componente (`EmbeddingGateway`) usado pelo pipeline e pela consulta.
- Lotes (batch) configuráveis (default 16 chunks/chamada) para amortizar overhead HTTP; falha transitória → retry, sustentada → circuit breaker com reenfileiramento (RF37).
- Validação pt-BR/inglês ([5.4] do plano): medida pelo golden set (RF33) **antes** do volume; troca de modelo = nova dimensão = reindexação completa (runbook em `resiliencia-e-operacao.md`).

## 6. Indexação vetorial (RF20)

- **Um índice único** `chunks-v1` (alias `chunks`) para todos os tenants, com isolamento por filtro de metadados — mapping em `dados.md`. Alternativa descartada: índice por tenant — isolamento físico mais forte, porém explosão de shards no cenário local e gerência de ciclo de vida de índice sem requisito que a justifique. O alias permite reindexação com troca atômica (`chunks-v2` → alias).
- Metadados obrigatórios em cada documento indexado: `chunkId`, `documentId`, `parentChunkId`, `ownerId`, `tenantId`, `isActive` — todos `keyword`/`boolean` (filtro exato, não análise textual). O texto do filho fica em `content` (analisado, para BM25).
- Escrita via `VectorStore` do Spring AI onde o contrato couber; a **busca híbrida** (k-NN + BM25 + filtros compostos) usa o cliente Java nativo do OpenSearch — o abstração do Spring AI não expressa RRF/BM25 combinados, e fingir que expressa custaria o design da consulta.
- Toda leitura aplica `tenantId + ownerId + isActive=true` — não existe método de busca sem esses parâmetros na assinatura (impossível esquecer por construção).

## 7. Decisões registradas nesta seção

| Decisão | Alternativa descartada | Motivo |
|---|---|---|
| Artefato intermediário `EXTRACTED` no storage | reextrair no retry de `TRANSFORMING` | OCR é a etapa mais cara; RF27 exige retomar sem repetir |
| Chunks autoritativos no Postgres | só OpenSearch/Neo4j | retry de embedding e composição de contexto precisam de leitura transacional e barata |
| `chunkId` determinístico (UUIDv5) | UUID aleatório | reprocessamento vira upsert idempotente (RF13); reconciliação (RF38) trivial |
| Tokenizer jtokkit aproximado atrás de porta | tokenizer exato do modelo | faixas do RF18 toleram aproximação; porta permite troca guiada por medição |
| CSV com cabeçalho repetido por chunk | fatiar cru | chunk sem cabeçalho é semanticamente cego |
| Prefixos `search_document:`/`search_query:` centralizados | embutir nos chamadores | requisito do modelo; esquecê-lo degrada silenciosamente |
| Índice único + alias versionado | índice por tenant | shards demais para o cenário; filtro keyword é o isolamento do RF20; alias viabiliza reindexação atômica |
