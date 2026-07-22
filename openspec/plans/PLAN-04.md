# PLAN-04 — Extração e Transformação (RF14–RF17)

> *Feature:* `extracao/extracao-e-transformacao.feature`
> *User story:* como pipeline, quero converter qualquer formato aceito em Markdown padronizado, com OCR quando não houver camada de texto.

**[4.1] Delegação por MIME** `[M · Must]` — factory de processadores segregados por tipo (RF14): PDF/imagem → Docling (`DoclingPdfDocumentReader`, reimplementar conforme ADR-002); MD/TXT/CSV/JSON/XML → leitores dedicados (Tika como apoio universal e detector de MIME).

**[4.2] Extração nativa + OCR** `[M · Must]` — texto nativo quando existe; OCR integrado (docling-serve) para JPG/PNG e PDFs escaneados (RF15).

**[4.3] Normalização para Markdown** `[M · Must]` — todo conteúdo extraído converge para Markdown padronizado (RF16), preservando estrutura (títulos, tabelas).

**[4.4] Armazenamento do transformado** `[P · Must]` — gravar em `/{tenantId}/{userId}/transformed/{fileId}/{version}.md` via `DocumentStorage` e atualizar status (RF17).
