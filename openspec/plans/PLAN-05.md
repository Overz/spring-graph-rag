# PLAN-05 — Pipeline Vetorial (RF18–RF20)

> *Features:* `pipeline-vetorial/chunking.feature`, `pipeline-vetorial/embeddings.feature`
> *User story:* como sistema de busca, quero chunks hierárquicos — filhos precisos para embedding, pais amplos para contexto — indexados com os metadados que garantem isolamento.

**[5.1] Chunking hierárquico** `[G · Must]` — chunk pai (seção, 1500–2000 tokens, contexto para a LLM no RF26) e chunk filho (300–500 tokens, unidade de embedding/indexação); relação pai-filho persistida; cada chunk com identificador, conteúdo, posição e documento de origem; CSV/JSON sem estrutura semântica usam tamanho fixo com overlap de 10–15% (RF18).

**[5.2] Geração de embeddings** `[M · Must]` — evento dispara a chamada ao modelo de embedding via Ollama (ADR-003) para os chunks filhos (RF19).

**[5.3] Armazenamento vetorial com metadados de filtro** `[M · Must]` — índice OpenSearch com `chunkId`, `documentId`, `ownerId`, `tenantId`, `isActive` mapeados como `keyword` (RF20); **toda** leitura do índice aplica os filtros — auditar que não existe query sem eles.

**[5.4] Validação do embedding em pt-BR/inglês** `[M · Must]` — medir `nomic-embed-text` contra o golden set (RF33) em consultas nos dois idiomas; se insuficiente, migrar para modelo multilíngue via Ollama (ex.: `bge-m3`) e registrar em ADR (troca exige reindexação — seção 4.2).
