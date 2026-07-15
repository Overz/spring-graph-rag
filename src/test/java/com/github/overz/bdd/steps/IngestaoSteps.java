package com.github.overz.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.overz.bdd.KeycloakTokens;
import com.github.overz.bdd.SyntheticFiles;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.PendingException;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps das features {@code ingestao/} (RF01–RF07), rodando E2E contra a app real
 * (Testcontainers + Keycloak). Convenções de leitura dos cenários:
 *
 * <ul>
 *   <li>Nomes lógicos de usuário ({@code dev_user}, {@code outro_user}) mapeiam para os
 *       usuários seedados do realm ({@code alice}, {@code bob}); {@code ownerId} real é a
 *       claim {@code sub} — os steps traduzem o nome lógico para o sub ao assertar.</li>
 *   <li>Identificadores literais dos cenários ({@code doc-123}, {@code {fileId}}) são
 *       simbólicos: aliases para o id real devolvido pelo upload.</li>
 *   <li>"status inicial RECEIVED": os estados RECEIVED/VALIDATING existem dentro da
 *       requisição síncrona (SDD ingestao §2, decisão D6) — o step valida pela primeira
 *       linha do histórico, não pelo status corrente ({@code UPLOADED}).</li>
 * </ul>
 */
public class IngestaoSteps {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*(KB|MB|GB)");
  private static final Map<String, String> REALM_USERS = Map.of(
    "dev_user", "alice",
    "outro_user", "bob"
  );

  @Autowired
  private JdbcTemplate jdbc;

  @Value("${local.server.port}")
  private int port;

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  @Value("${app.storage.base-dir}")
  private String storageBaseDir;

  private String logicalUser;
  private String tenant;
  private String token;

  private String fileName;
  private String declaredMime;
  private byte[] fileBytes;
  private final Map<String, byte[]> sentByFilename = new HashMap<>();
  private final Map<String, String> idAliases = new HashMap<>();

  private ResponseEntity<String> response;
  private long documentsBaseline;
  private long historyBaseline;

  /**
   * Isolamento entre cenários de ingestão: cada um parte de bases limpas — cotas e
   * documentos de um cenário não podem vazar para o seguinte (duplicidade/cota são
   * validações com estado).
   */
  @Before("@RF01 or @RF02 or @RF03 or @RF04 or @RF05 or @RF06 or @RF07")
  public void limparEstadoDeIngestao() throws IOException {
    jdbc.update("DELETE FROM document_status_history");
    jdbc.update("DELETE FROM processing_errors");
    jdbc.update("DELETE FROM documents");
    jdbc.update("DELETE FROM tenant_quotas");
    final var base = Path.of(storageBaseDir);
    if (Files.exists(base)) {
      try (Stream<Path> arquivos = Files.walk(base).sorted(java.util.Comparator.reverseOrder())) {
        arquivos.forEach(p -> p.toFile().delete());
      }
    }
  }

  // ---------------------------------------------------------------- contexto

  @Dado("que o usuário {string} do tenant {string} está autenticado")
  public void queOUsuarioStringDoTenantStringEstaAutenticado(String usuario, String tenantId) {
    autenticar(usuario, tenantId);
  }

  @Dado("um arquivo {string} do tipo {string} com tamanho de {string}")
  public void umArquivoStringDoTipoStringComTamanhoDe(String arquivo, String mime, String tamanho) {
    prepararArquivo(arquivo, mime, SyntheticFiles.of(arquivo, parseSize(tamanho)));
  }

  @Dado("que o usuário {string} tenta realizar o upload de um arquivo chamado {string}")
  public void queOUsuarioStringTentaRealizarOUploadDe(String usuario, String arquivo) {
    autenticar(usuario, tenant != null ? tenant : "acme_inc");
    prepararArquivo(arquivo, MediaType.APPLICATION_OCTET_STREAM_VALUE, SyntheticFiles.of("conteudo.txt", 1024));
  }

  @Dado("um arquivo {string} cujo conteúdo real é do tipo {string}")
  public void umArquivoStringCujoConteudoRealEDoTipo(String arquivo, String mimeReal) {
    // Conteúdo executável (magic MZ) disfarçado com a extensão do arquivo do cenário.
    prepararArquivo(arquivo, MediaType.APPLICATION_PDF_VALUE, SyntheticFiles.windowsExecutable());
  }

  @Dado("um arquivo {string} do tipo {string} cujo conteúdo está truncado e ilegível")
  public void umArquivoStringDoTipoStringCujoConteudoEsta(String arquivo, String mime) {
    prepararArquivo(arquivo, mime, SyntheticFiles.truncatedPdf());
  }

  @Dado("um arquivo {string} do tipo {string} contendo a assinatura de teste EICAR")
  public void umArquivoStringDoTipoStringContendoAAssinatura(String arquivo, String mime) {
    prepararArquivo(arquivo, mime, SyntheticFiles.pdfWithEicar());
  }

  @Dado("que o tenant {string} possui cota de armazenamento total de {string}")
  public void queOTenantStringPossuiCotaDeArmazenamentoTotal(String tenantId, String cota) {
    jdbc.update(
      "INSERT INTO tenant_quotas (tenant_id, max_storage_bytes, max_active_files) VALUES (?, ?, ?)",
      tenantId, (long) parseSize(cota), Integer.MAX_VALUE);
  }

  @Dado("o tenant {string} já ocupa {string} de armazenamento com arquivos ativos")
  public void oTenantStringJaOcupaStringDeArmazenamentoCom(String tenantId, String ocupado) {
    // Documento sintético direto na base: só o volume ativo importa para a validação de cota.
    jdbc.update("""
        INSERT INTO documents (id, tenant_id, owner_id, filename, extension, content_type,
          file_size_bytes, file_hash_sha256, raw_storage_key, status, version, is_active,
          lgpd_redacted, correlation_id, uploaded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        """,
      UUID.randomUUID(), tenantId, "seed-user", "volume-existente.pdf", "pdf", "application/pdf",
      (long) parseSize(ocupado), UUID.randomUUID().toString().replace("-", ""), "/seed/volume-existente.pdf",
      "COMPLETED", 1, true, false, "seed-correlation");
  }

  @Dado("que o usuário {string} do tenant {string} já enviou o arquivo {string} com sucesso")
  public void queOUsuarioStringDoTenantStringJaEnviou(String usuario, String tenantId, String arquivo) {
    autenticar(usuario, tenantId);
    prepararArquivo(arquivo, MediaType.APPLICATION_PDF_VALUE, SyntheticFiles.deterministicPdf(arquivo));
    enviar();
    assertThat(response.getStatusCode().value()).isEqualTo(202);
  }

  @Dado("que o usuário {string} do tenant {string} enviou o arquivo {string} e o processamento terminou com status {string}")
  public void queOUsuarioStringDoTenantStringEnviouO(String usuario, String tenantId, String arquivo, String status) {
    queOUsuarioStringDoTenantStringJaEnviou(usuario, tenantId, arquivo);
    final var id = UUID.fromString(bodyField("id"));
    jdbc.update("UPDATE documents SET status = ? WHERE id = ?", status, id);
  }

  @Dado("o hash SHA-{int} computado foi {string}")
  public void oHashShaIntComputadoFoiString(int bits, String hash) {
    // Informativo no cenário: o hash real é sempre computado do conteúdo enviado.
  }

  // ---------------------------------------------------------------- ações

  @Quando("o usuário enviar o arquivo para processamento")
  public void oUsuarioEnviarOArquivoParaProcessamento() {
    enviar();
  }

  @Quando("o sistema executar as validações iniciais de ingestão")
  public void oSistemaExecutarAsValidacoesIniciaisDeIngestao() {
    enviar();
  }

  @Quando("o sistema submeter o arquivo à varredura antimalware")
  public void oSistemaSubmeterOArquivoAVarreduraAntimalware() {
    enviar();
  }

  @Quando("o upload for aceito com o identificador {string}")
  public void oUploadForAceitoComOIdentificadorString(String alias) {
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    idAliases.put(alias, bodyField("id"));
  }

  @Quando("o usuário submeter o mesmo arquivo {string}")
  public void oUsuarioSubmeterOMesmoArquivoString(String arquivo) {
    prepararArquivo(arquivo, MediaType.APPLICATION_PDF_VALUE, sentByFilename.get(arquivo));
    enviar();
  }

  @Quando("o usuário {string} do tenant {string} submeter um arquivo com o mesmo hash SHA-{int}")
  public void oUsuarioStringDoTenantStringSubmeterUmArquivo(String usuario, String tenantId, int bits) {
    final var mesmoConteudo = fileBytes;
    autenticar(usuario, tenantId);
    prepararArquivo("copia-" + fileName, MediaType.APPLICATION_PDF_VALUE, mesmoConteudo);
    enviar();
  }

  @Quando("o usuário submeter o mesmo arquivo {string} com o comando explícito de reprocessamento")
  public void oUsuarioSubmeterOMesmoArquivoStringComO(String arquivo) {
    // Endpoint /reprocess nasce com o pipeline (Épico 2+) — cenário permanece @pendente.
    throw new PendingException();
  }

  // ---------------------------------------------------------------- asserções de aceite

  @Entao("o sistema deve aceitar o upload")
  public void oSistemaDeveAceitarOUpload() {
    assertThat(response.getStatusCode().value()).isEqualTo(202);
  }

  @Entao("deve responder com o identificador único do documento")
  public void deveResponderComOIdentificadorUnicoDoDocumento() {
    assertThat(UUID.fromString(bodyField("id"))).isNotNull();
  }

  @Entao("o status inicial do documento deve ser {string}")
  public void oStatusInicialDoDocumentoDeveSerString(String esperado) {
    final var primeiraTransicao = jdbc.queryForObject(
      "SELECT to_status FROM document_status_history WHERE document_id = ? ORDER BY id ASC LIMIT 1",
      String.class, UUID.fromString(bodyField("id")));
    assertThat(primeiraTransicao).isEqualTo(esperado);
  }

  @Entao("o documento deve prosseguir para o status {string}")
  public void oDocumentoDeveProsseguirParaOStatusString(String esperado) {
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(bodyField("status")).isEqualTo(esperado);
    final var persistido = jdbc.queryForObject(
      "SELECT status FROM documents WHERE id = ?", String.class, UUID.fromString(bodyField("id")));
    assertThat(persistido).isEqualTo(esperado);
  }

  @Entao("o arquivo deve ser aprovado na validação de tipo")
  public void oArquivoDeveSerAprovadoNaValidacaoDeTipo() {
    assertThat(response.getStatusCode().value())
      .as("aprovado na validação de tipo → upload aceito; corpo: %s", response.getBody())
      .isEqualTo(202);
  }

  @Entao("um identificador único deve ser gerado para o documento")
  public void umIdentificadorUnicoDeveSerGeradoParaODocumento() {
    assertThat(UUID.fromString(bodyField("id"))).isNotNull();
  }

  @Entao("a data de envio deve ser registrada")
  public void aDataDeEnvioDeveSerRegistrada() {
    final var uploadedAt = jdbc.queryForObject(
      "SELECT uploaded_at FROM documents WHERE id = ?", java.sql.Timestamp.class,
      UUID.fromString(bodyField("id")));
    assertThat(uploadedAt).isNotNull();
  }

  @Entao("o resultado do upload deve ser {string}")
  public void oResultadoDoUploadDeveSerString(String resultado) {
    if ("aceito".equals(resultado)) {
      assertThat(response.getStatusCode().value()).isEqualTo(202);
    } else {
      assertThat(response.getStatusCode().is4xxClientError())
        .as("esperava rejeição; status=%s corpo=%s", response.getStatusCode(), response.getBody())
        .isTrue();
    }
  }

  // ---------------------------------------------------------------- asserções de storage

  @Entao("o arquivo original deve ser salvo no Object Storage no caminho {string}")
  public void oArquivoOriginalDeveSerSalvoNoObjectStorage(String caminhoSimbolico) {
    final var chave = resolverChaveSimbolica(caminhoSimbolico);
    final var chavePersistida = jdbc.queryForObject(
      "SELECT raw_storage_key FROM documents WHERE id = ?", String.class,
      UUID.fromString(idAliases.getOrDefault("doc-123", bodyField("id"))));
    assertThat(chavePersistida).isEqualTo(chave);
    assertThat(Files.exists(arquivoNoStorage(chave))).as("artefato existe em %s", chave).isTrue();
  }

  @Entao("o conteúdo armazenado deve ser idêntico byte a byte ao arquivo enviado")
  public void oConteudoArmazenadoDeveSerIdenticoByteAByte() throws IOException {
    final var chave = jdbc.queryForObject(
      "SELECT raw_storage_key FROM documents WHERE id = ?", String.class,
      UUID.fromString(idAliases.getOrDefault("doc-123", bodyField("id"))));
    assertThat(Files.readAllBytes(arquivoNoStorage(chave))).isEqualTo(fileBytes);
  }

  @Entao("o arquivo não deve ser salvo no Object Storage em {string}")
  public void oArquivoNaoDeveSerSalvoNoObjectStorage(String estagio) {
    final var basename = Path.of(fileName.replace("\\", "/")).getFileName().toString();
    final var base = Path.of(storageBaseDir);
    if (!Files.exists(base)) {
      return;
    }
    try (Stream<Path> arquivos = Files.walk(base)) {
      assertThat(arquivos.filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().equals(basename)))
        .as("nenhum artefato '%s' gravado sob %s", basename, estagio)
        .isEmpty();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---------------------------------------------------------------- asserções de metadados

  @Entao("os seguintes metadados devem ser persistidos na base relacional:")
  public void osSeguintesMetadadosDevemSerPersistidosNaBaseRelacional(DataTable tabela) {
    final var id = UUID.fromString(bodyField("id"));
    final var linha = jdbc.queryForMap("SELECT * FROM documents WHERE id = ?", id);

    for (final var entrada : tabela.asMap(String.class, String.class).entrySet()) {
      final var campo = entrada.getKey();
      final var esperado = entrada.getValue();
      if ("campo".equals(campo)) {
        continue; // cabeçalho da tabela
      }
      switch (campo) {
        case "ownerId" -> assertThat(linha.get("owner_id")).isEqualTo(subDe(esperado));
        case "tenantId" -> assertThat(linha.get("tenant_id")).isEqualTo(esperado);
        case "nomeOriginal" -> assertThat(linha.get("filename")).isEqualTo(esperado);
        case "extensao" -> assertThat(linha.get("extension")).isEqualTo(esperado);
        case "tamanho" -> assertThat(((Number) linha.get("file_size_bytes")).longValue())
          .isEqualTo(Long.parseLong(esperado));
        case "hash" -> assertThat(linha.get("file_hash_sha256")).isEqualTo(sha256Hex(fileBytes));
        case "localizacao" -> assertThat(linha.get("raw_storage_key"))
          .isEqualTo(resolverChaveSimbolica(esperado));
        case "versao" -> assertThat(((Number) linha.get("version")).intValue())
          .isEqualTo(Integer.parseInt(esperado));
        // "status RECEIVED" da tabela é o status inicial — validado pelo histórico (D6).
        case "status" -> oStatusInicialDoDocumentoDeveSerString(esperado);
        default -> throw new IllegalArgumentException("Campo de metadado não mapeado: " + campo);
      }
    }
  }

  // ---------------------------------------------------------------- asserções de rejeição

  @Entao("o upload deve ser rejeitado com o motivo {string}")
  public void oUploadDeveSerRejeitadoComOMotivoString(String motivo) {
    assertRejeitadoCom(motivo);
  }

  @Entao("a requisição deve ser rejeitada com um erro de {string}")
  public void aRequisicaoDeveSerRejeitadaComUmErroDe(String motivo) {
    assertRejeitadoCom(motivo);
  }

  @Entao("a resposta deve orientar o usuário a pré-dividir documentos grandes")
  public void aRespostaDeveOrientarOUsuarioAPreDividir() {
    assertThat(bodyField("detail")).contains("Pré-divida");
  }

  @Entao("o sistema deve detectar o tipo MIME real pelo conteúdo, não pela extensão")
  public void oSistemaDeveDetectarOTipoMimeRealPelo() {
    // A extensão (.pdf) é suportada — só a detecção por conteúdo explica a rejeição.
    assertThat(response.getStatusCode().value()).isEqualTo(415);
  }

  @Entao("nenhum novo evento do ciclo de vida deve ser publicado")
  public void nenhumNovoEventoDoCicloDeVidaDeveSer() {
    assertThat(contarDocumentos()).isEqualTo(documentsBaseline);
    assertThat(contarHistorico()).isEqualTo(historyBaseline);
  }

  @Entao("a rejeição não deve consumir cota de reprocessamento do usuário")
  public void aRejeicaoNaoDeveConsumirCotaDeReprocessamentoDo() {
    // Nada persistido = nada contado (SDD ingestao §2): sem linha, não há consumo de cota.
    assertThat(contarDocumentos()).isEqualTo(documentsBaseline);
  }

  @Entao("a varredura não deve encontrar ameaças")
  public void aVarreduraNaoDeveEncontrarAmeacas() {
    assertThat(response.getStatusCode().value())
      .as("arquivo limpo passa a varredura; corpo: %s", response.getBody())
      .isEqualTo(202);
  }

  // ---------------------------------------------------------------- asserções de idempotência

  @Entao("o sistema deve interceptar a requisição na validação inicial")
  public void oSistemaDeveInterceptarARequisicaoNaValidacaoInicial() {
    assertThat(response.getStatusCode().value()).isEqualTo(409);
  }

  @Entao("deve rejeitar a operação para evitar duplicação de entidades e vetores")
  public void deveRejeitarAOperacaoParaEvitarDuplicacaoDeEntidades() {
    assertThat(bodyField("code")).isEqualTo("DUPLICATE_FILE");
  }

  @Entao("a idempotência deve considerar o escopo do usuário e tenant proprietários")
  public void aIdempotenciaDeveConsiderarOEscopoDoUsuarioE() {
    // O aceite do segundo dono prova o escopo: mesmo hash, donos distintos, duas linhas.
    final var donos = jdbc.queryForObject(
      "SELECT count(DISTINCT owner_id) FROM documents WHERE file_hash_sha256 = ?",
      Long.class, sha256Hex(fileBytes));
    assertThat(donos).isEqualTo(2);
  }

  @Entao("a idempotência deve considerar apenas envios anteriores com status de sucesso")
  public void aIdempotenciaDeveConsiderarApenasEnviosAnterioresComStatus() {
    assertThat(response.getStatusCode().value()).isEqualTo(202);
  }

  @Entao("o sistema deve aceitar a operação")
  public void oSistemaDeveAceitarAOperacao() {
    assertThat(response.getStatusCode().value()).isEqualTo(202);
  }

  @Entao("o pipeline completo deve ser reexecutado para o documento")
  public void oPipelineCompletoDeveSerReexecutadoParaODocumento() {
    throw new PendingException();
  }

  // ---------------------------------------------------------------- apoio

  private void autenticar(final String usuario, final String tenantId) {
    this.logicalUser = usuario;
    this.tenant = tenantId;
    this.token = KeycloakTokens.userToken(issuerUri, realmUser(usuario));
  }

  private void prepararArquivo(final String arquivo, final String mime, final byte[] conteudo) {
    this.fileName = arquivo;
    this.declaredMime = mime;
    this.fileBytes = conteudo;
    this.sentByFilename.put(arquivo, conteudo);
  }

  private void enviar() {
    documentsBaseline = contarDocumentos();
    historyBaseline = contarHistorico();

    final var resource = new ByteArrayResource(fileBytes) {
      @Override
      public String getFilename() {
        return fileName;
      }
    };
    final var partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.parseMediaType(declaredMime));
    final var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("file", new HttpEntity<>(resource, partHeaders));

    response = RestClient.builder()
      .baseUrl("http://localhost:" + port)
      .build()
      .post()
      .uri("/api/v1/documents")
      .headers(headers -> headers.setBearerAuth(token))
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .body(parts)
      .retrieve()
      .onStatus(status -> true, (request, clientResponse) -> { /* não lançar em 4xx/5xx */ })
      .toEntity(String.class);
  }

  private void assertRejeitadoCom(final String motivo) {
    assertThat(response.getStatusCode().is4xxClientError())
      .as("esperava rejeição; status=%s corpo=%s", response.getStatusCode(), response.getBody())
      .isTrue();
    final var body = body();
    final var detail = body.path("detail").asText("");
    final var code = body.path("code").asText("");
    assertThat(detail.contains(motivo) || code.equals(motivo))
      .as("motivo '%s' presente no detail ('%s') ou igual ao code ('%s')", motivo, detail, code)
      .isTrue();
  }

  /**
   * Traduz os elementos simbólicos dos cenários para a chave real: nome lógico de usuário →
   * {@code sub} (ownerId), {@code doc-123}/aliases → id real, {@code {fileId}} → último id aceito.
   */
  private String resolverChaveSimbolica(final String caminho) {
    var resolvido = caminho;
    if (logicalUser != null) {
      resolvido = resolvido.replace("/" + logicalUser + "/", "/" + subDe(logicalUser) + "/");
    }
    for (final var alias : idAliases.entrySet()) {
      resolvido = resolvido.replace("/" + alias.getKey() + "/", "/" + alias.getValue() + "/");
    }
    if (resolvido.contains("{fileId}")) {
      resolvido = resolvido.replace("{fileId}", bodyField("id"));
    }
    return resolvido;
  }

  private Path arquivoNoStorage(final String chave) {
    return Path.of(storageBaseDir, chave.split("/"));
  }

  private long contarDocumentos() {
    final var total = jdbc.queryForObject("SELECT count(*) FROM documents", Long.class);
    return total == null ? 0 : total;
  }

  private long contarHistorico() {
    final var total = jdbc.queryForObject("SELECT count(*) FROM document_status_history", Long.class);
    return total == null ? 0 : total;
  }

  private JsonNode body() {
    try {
      return JSON.readTree(response.getBody());
    } catch (IOException e) {
      throw new UncheckedIOException("Corpo de resposta não é JSON: " + response.getBody(), e);
    }
  }

  private String bodyField(final String campo) {
    final var node = body().path(campo);
    assertThat(node.isMissingNode()).as("campo '%s' presente no corpo %s", campo, response.getBody()).isFalse();
    return node.asText();
  }

  private String realmUser(final String logico) {
    return REALM_USERS.getOrDefault(logico, logico);
  }

  /**
   * {@code ownerId} real = claim {@code sub} do usuário — extraída do próprio JWT.
   */
  private String subDe(final String usuarioLogico) {
    final var jwt = KeycloakTokens.userToken(issuerUri, realmUser(usuarioLogico));
    final var payload = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    try {
      return JSON.readTree(payload).path("sub").asText();
    } catch (IOException e) {
      throw new UncheckedIOException("Payload de JWT ilegível", e);
    }
  }

  private static String sha256Hex(final byte[] conteudo) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int parseSize(final String tamanho) {
    final var matcher = SIZE.matcher(tamanho);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Tamanho não reconhecido: " + tamanho);
    }
    final var valor = Integer.parseInt(matcher.group(1));
    return switch (matcher.group(2)) {
      case "KB" -> valor * 1024;
      case "MB" -> valor * 1024 * 1024;
      case "GB" -> valor * 1024 * 1024 * 1024;
      default -> throw new IllegalArgumentException("Unidade não reconhecida: " + tamanho);
    };
  }

}
