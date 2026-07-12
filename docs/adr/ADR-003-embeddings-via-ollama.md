# ADR-003: Embeddings via Ollama (`nomic-embed-text`), Não Transformers/ONNX

## Status
Aceito

## Contexto

O `rag-plan.md` original (Épico 6.1) definia Ollama servindo `nomic-embed-text` como provedor de embeddings — mesma infraestrutura já usada para o modelo de chat (`qwen3:8b`). O `pom.xml` do projeto, porém, desde antes deste projeto adotar ADRs, usava `spring-ai-starter-model-transformers` (ONNX, roda in-process na JVM) em vez disso — decisão documentada apenas de forma breve no `CLAUDE.md` ("kept for simplicity"), nunca refletida de volta no `rag-plan.md`.

Ao revisar essa divergência, uma primeira versão desta ADR chegou a recomendar formalizar Transformers/ONNX como a escolha definitiva, com o argumento de que embeddings via Ollama competiriam por VRAM/slot de modelo carregado com o `qwen3:8b` — o `compose.yaml` já configura `OLLAMA_MAX_LOADED_MODELS=1`, assumindo um único modelo residente. Essa recomendação foi revista: **a decisão do projeto é usar Ollama**, porque a qualidade do embedding (retrieval de verdade, não um sentence-transformer genérico pequeno) pesa mais que o custo de acoplamento, e o problema de VRAM tem mitigação direta.

## Decisão

Embeddings são gerados via **Ollama, servindo `nomic-embed-text`** — o `EmbeddingModel` do Spring AI chama o mesmo servidor Ollama já usado para o chat, via HTTP.

Para evitar que o Ollama fique alternando (descarregando/recarregando) entre `qwen3:8b` e `nomic-embed-text` a cada troca entre chat e embedding:

- `OLLAMA_MAX_LOADED_MODELS` no `compose.yaml` passa de `1` para **`2`** — os dois modelos ficam residentes ao mesmo tempo.
- Custo de VRAM aceito: `qwen3:8b` (~5-6GB quantizado Q4) + `nomic-embed-text` (~274MB) cabem confortavelmente nos 8GB de VRAM disponíveis.
- `ollama-pull` (serviço one-shot do `compose.yaml`) baixa os dois modelos na subida, não só o de chat.

## Alternativas Consideradas

- **Transformers/ONNX (in-process)**: rejeitada. Decoupling de infraestrutura é real (sem dependência de rede, sem competir por VRAM), mas a qualidade do embedding — o que realmente importa pro recall de um RAG — é inferior à de um modelo dedicado a retrieval como `nomic-embed-text`. Latência de chamada HTTP local não é um problema prático neste projeto (confirmado: "não tem problema chamar HTTP").
- **`OLLAMA_MAX_LOADED_MODELS=1` (manter, aceitar reload sob demanda)**: rejeitada — cada alternância entre chat e embedding recarregaria o modelo (segundos, não milissegundos), custo real em qualquer ingestão concorrente com uso de chat, não só teórico.
- **Modelo mais forte para PT-BR (`bge-m3`/`qwen3-embedding`, sugestão do próprio rag-plan.md)**: não descartada — é uma evolução dentro da mesma decisão (Ollama como provedor), não uma alternativa a ela; considerar se a qualidade de `nomic-embed-text` em PT-BR se mostrar insuficiente (medir via Épico 13.5).

## Consequências

### Positivas
- Melhor qualidade de embedding de base (modelo dedicado a retrieval) sem esperar por um upgrade futuro.
- Uma só infraestrutura de IA local (Ollama) para chat e embedding, em vez de duas abstrações diferentes (ONNX in-process + Ollama).
- `OLLAMA_MAX_LOADED_MODELS=2` elimina o thrashing de modelo — ambos residentes.

### Negativas
- Geração de embedding em lote (Épico 6.2) agora depende de round-trip HTTP por chamada — latência maior que in-process, aceita.
- Pipeline de ingestão passa a depender da disponibilidade do Ollama também para embeddings, não só para chat/auto-tagging — uma indisponibilidade do Ollama agora afeta duas etapas do pipeline, não uma. Mitigado pela política de retry já prevista no Épico 8.3.
- Consumo de VRAM maior (~5-6GB + ~274MB vs. só ~5-6GB) — folga menor pra picos de contexto longo no chat; monitorar se voltar a ser um problema real.

## Plano de Ação

1. ~~Adicionar `spring-ai-starter-model-ollama` ao `pom.xml`, remover `spring-ai-starter-model-transformers`.~~ **Feito.**
2. ~~Configurar `spring.ai.ollama.embedding.options.model=nomic-embed-text` no `application.yaml`.~~ **Feito.**
3. ~~Ajustar `OLLAMA_MAX_LOADED_MODELS` para `2` e estender `ollama-pull` para baixar `nomic-embed-text` também.~~ **Feito.**
4. Corrigir `docs/rag-plan.md` (Épico 0.2, 6.1, tabela de stack, hardware, schema/config de referência) para refletir esta decisão.
5. Medir qualidade de recall real (Épico 13.5) antes de considerar migrar para `bge-m3`/`qwen3-embedding`.
