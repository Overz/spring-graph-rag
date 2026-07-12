# language: pt
@pendente
Funcionalidade: Extração de conteúdo e transformação para Markdown
  Cobre RF14 (identificação e delegação por tipo MIME), RF15 (extração nativa e
  OCR), RF16 (normalização para Markdown) e RF17 (armazenamento do transformado).

  Contexto:
    Dado que o usuário "dev_user" do tenant "acme_inc" está autenticado

  @RF14
  Esquema do Cenário: Delegação ao processador adequado por tipo MIME
    Dado que o documento "<arquivo>" do tipo "<mime>" está com status "QUEUED"
    Quando a etapa de extração iniciar
    Então o sistema deve delegar o processamento ao processador "<processador>"

    Exemplos:
      | arquivo    | mime             | processador        |
      | manual.pdf | application/pdf  | pdf-layout-ocr     |
      | foto.png   | image/png        | imagem-ocr         |
      | dados.csv  | text/csv         | dados-estruturados |
      | api.json   | application/json | dados-estruturados |
      | notas.txt  | text/plain       | texto-simples      |
      | leiame.md  | text/markdown    | texto-simples      |

  @RF15
  Cenário: Extração de conteúdo textual nativo de PDF com camada de texto
    Dado que o documento "relatorio-digital.pdf" possui camada textual nativa
    Quando a etapa "EXTRACTING" processar o documento
    Então o conteúdo textual deve ser extraído diretamente da camada nativa
    E o OCR não deve ser acionado

  @RF15
  Cenário: OCR acionado para PDF escaneado sem camada textual
    Dado que o documento "contrato-escaneado.pdf" contém apenas imagens de páginas digitalizadas
    Quando a etapa "EXTRACTING" processar o documento
    Então o sistema deve acionar o OCR integrado para reconhecer o texto das páginas
    E o texto reconhecido deve ser incluído no resultado da extração

  @RF15
  Cenário: OCR extrai texto de imagem enviada diretamente
    Dado que o documento "quadro-branco.jpg" é uma fotografia contendo texto impresso
    Quando a etapa "EXTRACTING" processar o documento
    Então o OCR deve reconhecer o texto presente na imagem

  @RF16
  Cenário: Normalização do conteúdo extraído para Markdown padronizado
    Dado que a extração do documento "manual.pdf" produziu títulos, parágrafos, listas e uma tabela
    Quando a etapa "TRANSFORMING" normalizar o conteúdo
    Então o resultado deve ser um único documento Markdown padronizado
    E os títulos devem virar cabeçalhos Markdown preservando a hierarquia
    E a tabela deve ser convertida para a sintaxe de tabelas Markdown

  @RF17
  Cenário: Armazenamento do Markdown transformado e atualização de status
    Dado que o documento "manual.pdf" com identificador "doc-456" e versão 1 concluiu a normalização
    Quando a etapa "TRANSFORMING" finalizar
    Então o arquivo Markdown deve ser salvo no Object Storage no caminho "/acme_inc/dev_user/transformed/doc-456/1.md"
    E o status do documento deve ser atualizado na base relacional
