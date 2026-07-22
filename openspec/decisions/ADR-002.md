# ADR-002: Parsing de PDF e Imagem com Docling em vez de Apache Tika

## Status
Aceito

## Contexto

O Épico 2.1 do `docs/rag-plan.md` previa `TikaDocumentReader` como leitor universal para todos os formatos (PDF, DOCX, PPTX, HTML, RTF, imagens...). Tika extrai texto de forma confiável na maioria dos formatos de escritório, mas em PDF — o formato mais comum de documento de negócio, e o mais problemático — sua extração é literal e não entende layout: colunas viram texto embaralhado, tabelas viram texto corrido sem estrutura, e PDF digitalizado (só imagem, sem camada de texto) não é lido de forma alguma sem OCR.

[Docling](https://github.com/docling-project/docling) resolve especificamente isso: layout analysis, reconhecimento de estrutura de tabela e OCR nativo para digitalizados — mantendo saída em Markdown/estruturada em vez de texto corrido. É a decisão certa para o objetivo do Épico 2 ("converter arquivos de formatos variados em texto estruturado").

A implicação de infraestrutura não é trivial: **Docling Java (`docling-java`) não é uma biblioteca que roda in-process na JVM.** É um client HTTP para o **Docling Serve** — um backend Python separado (FastAPI + os modelos de layout/OCR/tabela do Docling). Isso muda o formato da decisão de "trocar uma dependência Maven" para "adicionar um serviço".

## Decisão

1. **`docling-serve` entra no `compose.yaml`** como novo serviço, imagem `quay.io/docling-project/docling-serve-cpu` — variante **CPU-only**, não a variante CUDA. Motivo: a GPU do host (8GB VRAM) já está inteiramente comprometida com o Ollama (`qwen3:8b`, ver Épico 0.2/3.2); rodar Docling também em GPU faria os dois disputarem o mesmo VRAM por uma única placa.
2. **OCR (`do_ocr`) e estrutura de tabela (`do_table_structure`) ficam nos valores default do próprio Docling Serve** (`true` para os dois) — cobre exatamente o caso de uso pedido ("extração de dados em PDF e imagens") sem precisar de download de modelo extra: são os modelos já embutidos na imagem base, não os modelos avançados opcionais (descrição de imagem, extração de gráfico, VLM) que exigiriam um passo de download adicional.
3. **`DoclingPdfDocumentReader`** (novo, `com.github.overz.rag.internal`) implementa a interface `DocumentReader` do Spring AI — mesma abstração que qualquer outro reader do pipeline (Épico 2.1), então o restante do Épico 2/3 (sanitização, chunking) não sabe nem precisa saber que o parsing de PDF/imagem passa por uma chamada HTTP em vez de rodar in-process.
4. **Tika não é removido do projeto.** Continua sendo:
   - o leitor universal para os formatos que não são PDF/imagem (DOCX, PPTX, HTML, RTF — Épico 2.1);
   - o detector de MIME real no upload (Épico 1.2);
   - a fonte de metadados nativos do arquivo (Épico 2.2).

   Nenhuma dessas três responsabilidades é afetada por esta ADR — só a extração de conteúdo de PDF/imagem migrou.

## Alternativas Consideradas

- **Manter `TikaDocumentReader` também para PDF**: rejeitada — é justamente a fraqueza que motivou a troca (layout, tabela, digitalizado).
- **PDFBox direto**: rejeitada — mesma classe de limitação que Tika para PDF (extração literal, sem entendimento de layout); Tika internamente já usa PDFBox para PDF, então seria uma troca sem ganho real.
- **Variante GPU do Docling Serve (`-cu128`/`-cu130`)**: rejeitada por ora — disputaria a única GPU do host com o Ollama. Fica como possível revisão futura se o host ganhar uma segunda GPU, ou se a latência de parsing em CPU se mostrar um problema real medido (hoje é suposição, não medição).
- **Rodar o modelo Docling in-process via alguma binding nativa Python↔JVM**: não existe essa opção hoje no ecossistema Docling — o próprio projeto adota o modelo cliente/servidor deliberadamente (isola dependências pesadas de ML do runtime da aplicação).

## Consequências

### Positivas
- Extração de PDF/imagem muito melhor — layout, tabela, OCR de digitalizado — sem esforço de implementação própria (regex/heurística para tentar reconstruir estrutura, como o Épico 5.3 já cogitava como melhoria incremental).
- Isolamento de dependências pesadas (PyTorch, modelos de ML) fora do processo Java — a JVM da aplicação continua leve.
- Interface `DocumentReader` já abstrai a diferença; o resto do pipeline não muda.

### Negativas
- **Mais um serviço de infraestrutura** para rodar/monitorar em dev local — mitigado: sem GPU, sem volume de modelo (já embutido na imagem), `restart: always` como os demais.
- **Latência de parsing maior que a de uma lib in-process**, por ser uma chamada HTTP para um processo Python fazendo inferência em CPU. Aceitável para o volume de um caso de estudo; se o pipeline de produção real precisasse de throughput maior, a variante GPU (rejeitada acima) ou múltiplos workers do Docling Serve (suportado nativamente, ver `docs/deploy-examples/docling-serve-rq-workers.yaml` do próprio projeto) seriam o próximo passo.
- **Acoplamento a um serviço externo durante o parsing** — se `docling-serve` estiver fora do ar, upload de PDF/imagem falha nesse estágio. Mesma categoria de risco que já existe com Ollama/OpenSearch; tratamento de retry (Épico 8.3) já cobre esse cenário de forma genérica.

## Plano de Ação

1. ~~Adicionar `docling-serve` (imagem `-cpu`) ao `compose.yaml`.~~ **Feito.**
2. ~~Adicionar `docling-bom`/`docling-serve-api`/`docling-serve-client`/`docling-testcontainers` ao `pom.xml`, remover `spring-ai-pdf-document-reader`.~~ **Feito.**
3. ~~Implementar `DoclingPdfDocumentReader` (`com.github.overz.rag.internal`) e o bean `DoclingServeApi`.~~ **Feito.**
4. Estender a factory de seleção de `DocumentReader` por `content-type` (Épico 2.1) para rotear PDF/imagem para `DoclingPdfDocumentReader` e o restante para Tika/Markdown/CSV — ainda não implementada nenhuma factory, é o próximo passo natural do Épico 2.
5. Medir latência real de parsing (Épico 13) antes de considerar a variante GPU — hoje é decisão sem dado, revisitar se necessário.
