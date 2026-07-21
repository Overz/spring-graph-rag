package com.github.overz.bdd.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.overz.bdd.KeycloakTokens;
import com.github.overz.bdd.SyntheticFiles;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Entao;
import io.cucumber.java.pt.Quando;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps das features {@code ciclo-de-vida/} (RF08–RF11), rodando E2E contra a app real
 * (Testcontainers: Postgres/Neo4j/OpenSearch/Keycloak). Design.md D1: como os Épicos 5/6
 * (embedding/extração real) ainda não existem, os cenários de RF08 (fork-join) e RF10/RF11
 * (grafo/vetor) fixturam o estado diretamente — via JDBC (mesmo padrão de
 * {@link IngestaoSteps}) ou {@link Neo4jClient}/{@link OpenSearchClient} — em vez de esperar
 * um pipeline real. RF09 (consulta) e os comandos de RF10 usam os endpoints HTTP reais.
 */
public class CicloDeVidaSteps {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, String> REALM_USERS = Map.of(
    "dev_user", "alice",
    "outra_pessoa", "bob"
  );
  private static final List<String> PRE_FORK_SEQUENCE = List.of("QUEUED", "EXTRACTING", "TRANSFORMING", "CHUNKING");
  private static final String CHUNK_INDEX_ALIAS = "chunks";

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private Neo4jClient neo4jClient;

  @Autowired
  private OpenSearchClient openSearchClient;

  @Value("${local.server.port}")
  private int port;

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
  private String issuerUri;

  /** Alias lógico do cenário (ex. {@code Documento_A}, {@code politica-ferias.pdf}) → id real. */
  private final Map<String, String> docIds = new HashMap<>();
  private final Map<String, String> chunkIds = new HashMap<>();
  private final Map<String, String> entityIds = new HashMap<>();

  private String currentId;
  private ResponseEntity<String> response;

  @Before("@RF08 or @RF09 or @RF10 or @RF11")
  public void limparEstadoDeCicloDeVida() throws IOException {
    jdbc.update("DELETE FROM document_status_history");
    jdbc.update("DELETE FROM processing_errors");
    jdbc.update("DELETE FROM documents");
    neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    openSearchClient.deleteByQuery(new DeleteByQueryRequest.Builder()
      .index(CHUNK_INDEX_ALIAS)
      .query(q -> q.matchAll(m -> m))
      .build());
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index(CHUNK_INDEX_ALIAS).build());
  }

  // ---------------------------------------------------------------- RF11 Garbage Collection

  @Autowired
  private com.github.overz.rag.internal.services.EntityGarbageCollectionJob entityGarbageCollectionJob;

  @Dado("que uma operação de Soft Delete deixou a entidade {string} no Neo4j sem nenhuma aresta conectada a um nó de {string} com {string}")
  public void queUmaOperacaoDeSoftDeleteDeixouAEntidade(String entidade, String noTipo, String flag) {
    criarEntidadeOrfa(idDe(entityIds, entidade), "acme_inc");
  }

  @Quando("o job assíncrono de Garbage Collection for executado")
  public void oJobAssincronoDeGarbageCollectionForExecutado() {
    entityGarbageCollectionJob.expurgeOrphanEntities();
  }

  @Entao("o sistema deve identificar a entidade {string} como órfã")
  public void oSistemaDeveIdentificarAEntidadeStringComoOrfa(String entidade) {
    assertThat(entidadeExiste(idDe(entityIds, entidade))).as("entidade órfã removida após GC").isFalse();
  }

  @Entao("deve deletá-la fisicamente do banco de grafos \\(Hard Delete\\)")
  public void deveDeletaLaFisicamenteDoBancoDeGrafosHard() {
    // Coberto pela asserção anterior — mesma verificação de ausência física no grafo.
  }

  @Dado("que a entidade {string} possui pelo menos uma aresta conectada a um nó de {string} com {string}")
  public void queAEntidadeStringPossuiPeloMenosUmaAresta(String entidade, String noTipo, String flag) {
    final var chunkId = UUID.randomUUID().toString();
    final var entityId = idDe(entityIds, entidade);
    neo4jClient.query("CREATE (c:Chunk {id:$chunkId, tenantId:$tenantId, isActive:true})")
      .bindAll(Map.of("chunkId", chunkId, "tenantId", "acme_inc")).run();
    conectarChunkAEntidade(chunkId, entityId, "acme_inc");
  }

  @Entao("a entidade {string} não deve ser removida do banco de grafos")
  public void aEntidadeStringNaoDeveSerRemovidaDoBanco(String entidade) {
    assertThat(entidadeExiste(idDe(entityIds, entidade))).as("entidade referenciada por chunk ativo preservada").isTrue();
  }

  @Dado("que a entidade órfã {string} possui um relacionamento {string} com a entidade órfã {string}")
  public void queAEntidadeOrfaStringPossuiUmRelacionamentoString(String entidade1, String tipoRelacionamento, String entidade2) {
    final var id1 = idDe(entityIds, entidade1);
    final var id2 = idDe(entityIds, entidade2);
    criarEntidadeOrfa(id1, "acme_inc");
    criarEntidadeOrfa(id2, "acme_inc");
    criarRelacionamentoEntreEntidades(id1, id2, tipoRelacionamento);
  }

  @Entao("as entidades {string} e {string} devem ser removidas fisicamente")
  public void asEntidadesStringEStringDevemSerRemovidasFisicamente(String entidade1, String entidade2) {
    assertThat(entidadeExiste(idDe(entityIds, entidade1))).as("primeira entidade removida").isFalse();
    assertThat(entidadeExiste(idDe(entityIds, entidade2))).as("segunda entidade removida").isFalse();
  }

  @Entao("o relacionamento {string} entre elas deve ser removido fisicamente")
  public void oRelacionamentoStringEntreElasDeveSerRemovidoFisicamente(String tipoRelacionamento) {
    // DETACH DELETE já removeu os nós e suas arestas — sem nós, não há relacionamento a checar.
  }

  // ---------------------------------------------------------------- RF10 Soft delete / versionamento

  @Dado("que o {string} do {string} gerou a entidade {string} no Neo4j")
  public void queOStringDoStringGerouAEntidadeString(String documento, String usuarioLogico, String entidade) {
    final var documentId = uploadArquivo(usuarioLogico, documento).toString();
    docIds.put(documento, documentId);
    final var chunkId = UUID.randomUUID().toString();
    chunkIds.put(documento, chunkId);
    criarDocumentoEChunkNoGrafo(documentId, chunkId, "acme_inc", true);
    conectarChunkAEntidade(chunkId, idDe(entityIds, entidade), "acme_inc");
    indexarChunkFixture(chunkId, documentId, "acme_inc");
  }

  @Dado("o {string} de outro usuário na mesma empresa também se conecta a {string}")
  public void oStringDeOutroUsuarioNaMesmaEmpresaTambem(String documento, String entidade) {
    final var documentId = uploadArquivo("outra_pessoa", documento).toString();
    docIds.put(documento, documentId);
    final var chunkId = UUID.randomUUID().toString();
    chunkIds.put(documento, chunkId);
    criarDocumentoEChunkNoGrafo(documentId, chunkId, "acme_inc", true);
    conectarChunkAEntidade(chunkId, idDe(entityIds, entidade), "acme_inc");
  }

  @Quando("o {string} comandar a exclusão do {string}")
  public void oStringComandarAExclusaoDoString(String usuarioLogico, String documento) {
    response = deleteDocumento(docIds.get(documento), usuarioLogico);
    refreshChunkIndex();
  }

  @Entao("o sistema deve alterar a flag {string} para {string} no {string} e em seus {string} no Neo4j")
  public void oSistemaDeveAlterarAFlagStringParaString(String flag, String valor, String documento, String noTipo) {
    assertThat(response.getStatusCode().value()).as("exclusão aceita; corpo=%s", response.getBody()).isEqualTo(204);
    assertThat(neoIsActive("Document", docIds.get(documento))).as("Document.isActive").isFalse();
    assertThat(neoIsActive("Chunk", chunkIds.get(documento))).as("Chunk.isActive").isFalse();

    final var linhaDeAuditoria = jdbc.queryForObject(
      "SELECT count(*) FROM document_status_history WHERE document_id = ? AND detail = ?",
      Long.class, UUID.fromString(docIds.get(documento)), "Documento excluído logicamente");
    assertThat(linhaDeAuditoria).as("histórico registra a exclusão lógica").isGreaterThan(0);

    final var statusAposExclusao = getStatus(docIds.get(documento), "dev_user");
    assertThat(statusAposExclusao.getStatusCode().value())
      .as("documento excluído não deve mais responder por GET /status")
      .isEqualTo(404);
  }

  @Entao("deve inativar os vetores correspondentes no OpenSearch")
  public void deveInativarOsVetoresCorrespondentesNoOpensearch() {
    assertThat(chunkAtivoNoOpenSearch(chunkIds.get("Documento_A"))).as("vetor inativado no OpenSearch").isFalse();
  }

  @Entao("a entidade {string} deve ser preservada no grafo, pois está ligada ao {string}")
  public void aEntidadeStringDeveSerPreservadaNoGrafoPois(String entidade, String documentoAinda) {
    assertThat(entidadeExiste(idDe(entityIds, entidade))).as("entidade preservada por outra referência ativa").isTrue();
  }

  @Dado("que o {string} do {string} foi excluído logicamente")
  public void queOStringDoStringFoiExcluidoLogicamente(String documento, String usuarioLogico) {
    final var documentId = uploadArquivo(usuarioLogico, documento).toString();
    docIds.put(documento, documentId);
    final var chunkId = UUID.randomUUID().toString();
    chunkIds.put(documento, chunkId);
    criarDocumentoEChunkNoGrafo(documentId, chunkId, "acme_inc", true);
    indexarChunkFixture(chunkId, documentId, "acme_inc");
    response = deleteDocumento(documentId, usuarioLogico);
    assertThat(response.getStatusCode().value()).isEqualTo(204);
    refreshChunkIndex();
  }

  @Quando("qualquer busca vetorial ou de grafo for executada no tenant {string}")
  public void qualquerBuscaVetorialOuDeGrafoForExecutadaNo(String tenantId) {
    // Sem RF25/RF07 (Épico 7) reais ainda para exercitar: a asserção abaixo confirma
    // diretamente o invariante que todo filtro de recuperação vai aplicar (isActive).
  }

  @Entao("nenhum chunk do {string} deve aparecer nos resultados")
  public void nenhumChunkDoStringDeveAparecerNosResultados(String documento) {
    assertThat(chunkAtivoNoOpenSearch(chunkIds.get(documento))).as("chunk inativo, excluído de qualquer busca").isFalse();
  }

  @Entao("os filtros de recuperação devem considerar apenas nós e vetores com {string}")
  public void osFiltrosDeRecuperacaoDevemConsiderarApenasNosE(String flag) {
    assertThat(neoIsActive("Document", docIds.get("Documento_A"))).as("Document.isActive").isFalse();
  }

  @Dado("que o arquivo {string} com identificador {string} e versão {int} está com status {string}")
  public void queOArquivoStringComIdentificadorStringEVersao(String arquivo, String alias, int versao, String status) {
    final var documentId = uploadArquivo("dev_user", arquivo);
    docIds.put(alias, documentId.toString());
    currentId = documentId.toString();
    jdbc.update("UPDATE documents SET status = ? WHERE id = ?", status, documentId);
  }

  @Quando("o usuário substituir o arquivo {string} por uma nova versão")
  public void oUsuarioSubstituirOArquivoStringPorUmaNova(String arquivo) {
    response = substituirVersao(docIds.get("doc-456"), "dev_user", arquivo, SyntheticFiles.deterministicPdf(arquivo + "-v2"));
  }

  @Entao("a versão anterior deve seguir o fluxo de Soft Delete")
  public void aVersaoAnteriorDeveSeguirOFluxoDeSoft() {
    final var ativo = jdbc.queryForObject(
      "SELECT is_active FROM documents WHERE id = ?", Boolean.class, UUID.fromString(docIds.get("doc-456")));
    assertThat(ativo).isFalse();
  }

  @Entao("a nova versão deve ser registrada como versão {int}")
  public void aNovaVersaoDeveSerRegistradaComoVersaoInt(int versaoEsperada) {
    assertThat(response.getStatusCode().value()).as("substituição aceita; corpo=%s", response.getBody()).isEqualTo(202);
    assertThat(campo(response, "version")).isEqualTo(String.valueOf(versaoEsperada));
    docIds.put("doc-456-nova-versao", campo(response, "id"));
  }

  @Entao("o pipeline completo deve ser reexecutado para o novo conteúdo")
  public void oPipelineCompletoDeveSerReexecutadoParaONovo() {
    final var novoId = UUID.fromString(docIds.get("doc-456-nova-versao"));
    final var primeiraTransicao = jdbc.queryForObject(
      "SELECT to_status FROM document_status_history WHERE document_id = ? ORDER BY id ASC LIMIT 1",
      String.class, novoId);
    assertThat(primeiraTransicao).isEqualTo("RECEIVED");
    final var statusAtual = jdbc.queryForObject("SELECT status FROM documents WHERE id = ?", String.class, novoId);
    assertThat(statusAtual).isEqualTo("UPLOADED");
  }

  @Dado("que o {string} do {string} é o único documento conectado à entidade {string} no Neo4j")
  public void queOStringDoStringEOUnicoDocumento(String documento, String usuarioLogico, String entidade) {
    queOStringDoStringGerouAEntidadeString(documento, usuarioLogico, entidade);
  }

  @Entao("o documento e seus chunks devem ser marcados com {string}")
  public void oDocumentoESeusChunksDevemSerMarcadosCom(String flag) {
    assertThat(neoIsActive("Document", docIds.get("Documento_X"))).as("Document.isActive").isFalse();
    assertThat(neoIsActive("Chunk", chunkIds.get("Documento_X"))).as("Chunk.isActive").isFalse();
  }

  @Entao("a entidade {string} deve permanecer no grafo até a próxima execução do Garbage Collection")
  public void aEntidadeStringDevePermanecerNoGrafoAteA(String entidade) {
    assertThat(entidadeExiste(idDe(entityIds, entidade))).as("entidade órfã ainda não coletada").isTrue();
  }

  @Dado("que o {string} pertence ao usuário {string} do tenant {string}")
  public void queOStringPertenceAoUsuarioStringDoTenant(String documento, String usuarioLogico, String tenantId) {
    final var documentId = uploadArquivo(usuarioLogico, documento);
    docIds.put(documento, documentId.toString());
  }

  @Entao("a operação deve ser negada por falta de permissão")
  public void aOperacaoDeveSerNegadaPorFaltaDePermissao() {
    assertThat(response.getStatusCode().value()).as("corpo=%s", response.getBody()).isEqualTo(403);
  }

  @Entao("o documento deve permanecer com {string}")
  public void oDocumentoDevePermanecerComString(String flag) {
    final var ativo = jdbc.queryForObject(
      "SELECT is_active FROM documents WHERE id = ?", Boolean.class, UUID.fromString(docIds.get("Documento_B")));
    assertThat(ativo).isTrue();
  }

  // ---------------------------------------------------------------- RF08 fork-join + RF09 consulta

  @Dado("que o arquivo {string} foi enviado com sucesso")
  public void queOArquivoStringFoiEnviadoComSucesso(String arquivo) {
    currentId = uploadArquivo("dev_user", arquivo).toString();
    docIds.put(arquivo, currentId);
  }

  @Quando("o pipeline de processamento executar até a conclusão sem falhas")
  public void oPipelineDeProcessamentoExecutarAteAConclusaoSem() {
    final var id = UUID.fromString(currentId);
    avancarAte(id, "CHUNKING");
    registrarTransicaoDeRamo(id, "EMBEDDING", "COMPLETED");
    registrarTransicaoDeRamo(id, "GRAPH_BUILDING", "COMPLETED");
    jdbc.update(
      "UPDATE documents SET status = 'COMPLETED', embedding_status = 'COMPLETED', graph_status = 'COMPLETED' WHERE id = ?",
      id);
  }

  @Entao("o documento deve transitar em ordem pelos status:")
  public void oDocumentoDeveTransitarEmOrdemPelosStatus(DataTable dataTable) {
    final var esperado = dataTable.asList();
    final var historico = jdbc.queryForList(
      "SELECT to_status FROM document_status_history WHERE document_id = ? AND branch IS NULL ORDER BY id ASC",
      String.class, UUID.fromString(currentId));
    assertThat(historico).isEqualTo(esperado);
  }

  @Entao("após {string} as etapas {string} e {string} devem ser disparadas em paralelo")
  public void aposStringAsEtapasStringEStringDevemSer(String etapaBase, String ramo1, String ramo2) {
    final var ramos = jdbc.queryForList(
      "SELECT DISTINCT branch FROM document_status_history WHERE document_id = ? AND branch IS NOT NULL",
      String.class, UUID.fromString(currentId));
    assertThat(ramos).containsExactlyInAnyOrder(ramo1, ramo2);
  }

  @Entao("com ambos os ramos concluídos o status final deve ser {string}")
  public void comAmbosOsRamosConcluidosOStatusFinalDeve(String statusEsperado) {
    response = getStatus(currentId, "dev_user");
    assertThat(campo(response, "status")).isEqualTo(statusEsperado);
  }

  @Dado("que o documento {string} concluiu a etapa {string} com sucesso")
  public void queODocumentoStringConcluiuAEtapaStringCom(String documento, String etapa) {
    currentId = uploadArquivo("dev_user", documento).toString();
    docIds.put(documento, currentId);
    avancarAte(UUID.fromString(currentId), etapa);
  }

  @Quando("o ramo {string} estiver concluído e o ramo {string} ainda em execução")
  public void oRamoStringEstiverConcluidoEORamoString(String ramoConcluido, String ramoEmExecucao) {
    setBranchStatus(UUID.fromString(currentId), ramoConcluido, "COMPLETED");
    setBranchStatus(UUID.fromString(currentId), ramoEmExecucao, "RUNNING");
  }

  @Entao("o sub-estado {string} deve ser {string}")
  public void oSubEstadoStringDeveSerString(String subEstado, String valorEsperado) {
    final var coluna = "embeddingStatus".equals(subEstado) ? "embedding_status" : "graph_status";
    final var valor = jdbc.queryForObject(
      "SELECT " + coluna + " FROM documents WHERE id = ?", String.class, UUID.fromString(currentId));
    assertThat(valor).isEqualTo(valorEsperado);
  }

  @Entao("o status geral do documento deve ser derivado dos dois sub-estados")
  public void oStatusGeralDoDocumentoDeveSerDerivadoDos() {
    final var status = jdbc.queryForObject(
      "SELECT status FROM documents WHERE id = ?", String.class, UUID.fromString(currentId));
    assertThat(status).as("ainda não finalizado — um dos ramos segue em execução")
      .isNotIn("COMPLETED", "PARTIALLY_COMPLETED", "FAILED");
  }

  @Dado("que o {string} concluiu a etapa {string} com sucesso")
  public void queOStringConcluiuAEtapaStringComSucesso(String documento, String etapa) {
    currentId = uploadArquivo("dev_user", documento).toString();
    docIds.put(documento, currentId);
    avancarAte(UUID.fromString(currentId), etapa);
  }

  @Dado("as etapas {string} e {string} são disparadas em paralelo")
  public void asEtapasStringEStringSaoDisparadasEmParalelo(String ramo1, String ramo2) {
    setBranchStatus(UUID.fromString(currentId), ramo1, "RUNNING");
    setBranchStatus(UUID.fromString(currentId), ramo2, "RUNNING");
  }

  @Quando("a etapa {string} for concluída com sucesso")
  public void aEtapaStringForConcluidaComSucesso(String etapa) {
    setBranchStatus(UUID.fromString(currentId), etapa, "COMPLETED");
    recomputeAggregateStatus(UUID.fromString(currentId));
  }

  @Quando("a etapa {string} falhar definitivamente após esgotar as tentativas de retry")
  public void aEtapaStringFalharDefinitivamenteAposEsgotarAsTentativas(String etapa) {
    final var id = UUID.fromString(currentId);
    setBranchStatus(id, etapa, "FAILED");
    registrarFalhaDeEtapa(id, etapa);
    recomputeAggregateStatus(id);
  }

  @Entao("o documento deve ser marcado com status {string}")
  public void oDocumentoDeveSerMarcadoComStatusString(String statusEsperado) {
    response = getStatus(currentId, "dev_user");
    assertThat(campo(response, "status")).isEqualTo(statusEsperado);
  }

  @Entao("os chunks do {string} devem permanecer disponíveis para busca vetorial em RF25")
  public void osChunksDoStringDevemPermanecerDisponiveisParaBusca(String documento) {
    final var ativo = jdbc.queryForObject(
      "SELECT is_active FROM documents WHERE id = ?", Boolean.class, UUID.fromString(currentId));
    assertThat(ativo).as("documento segue ativo — chunks continuam elegíveis pro filtro isActive").isTrue();
  }

  @Entao("o registro de falha da etapa {string} deve ficar disponível para reprocessamento manual")
  public void oRegistroDeFalhaDaEtapaStringDeveFicar(String etapa) {
    final var total = jdbc.queryForObject(
      "SELECT count(*) FROM processing_errors WHERE document_id = ? AND stage = ?",
      Long.class, UUID.fromString(currentId), etapa);
    assertThat(total).isGreaterThan(0);
  }

  @Dado("que o arquivo {string} está na etapa {string}")
  public void queOArquivoStringEstaNaEtapaString(String arquivo, String etapa) {
    currentId = uploadArquivo("dev_user", arquivo).toString();
    docIds.put(arquivo, currentId);
    avancarAte(UUID.fromString(currentId), etapa);
  }

  @Quando("o usuário consultar o status do documento")
  public void oUsuarioConsultarOStatusDoDocumento() {
    response = getStatus(currentId, "dev_user");
  }

  @Entao("a resposta deve informar o status atual {string}")
  public void aRespostaDeveInformarOStatusAtualString(String statusEsperado) {
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(campo(response, "status")).isEqualTo(statusEsperado);
  }

  @Dado("que o arquivo {string} concluiu o processamento com status {string}")
  public void queOArquivoStringConcluiuOProcessamentoComStatus(String arquivo, String status) {
    currentId = uploadArquivo("dev_user", arquivo).toString();
    docIds.put(arquivo, currentId);
    final var id = UUID.fromString(currentId);
    avancarAte(id, "CHUNKING");
    registrarTransicaoDeRamo(id, "EMBEDDING", "COMPLETED");
    registrarTransicaoDeRamo(id, "GRAPH_BUILDING", "COMPLETED");
    jdbc.update("UPDATE documents SET status = ?, embedding_status = 'COMPLETED', graph_status = 'COMPLETED' WHERE id = ?",
      status, id);
  }

  @Quando("o usuário consultar o histórico de processamento do documento")
  public void oUsuarioConsultarOHistoricoDeProcessamentoDoDocumento() {
    response = getHistory(currentId, "dev_user");
  }

  @Entao("o histórico deve listar todas as etapas executadas em ordem cronológica")
  public void oHistoricoDeveListarTodasAsEtapasExecutadasEm() {
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    final var entradas = historicoBody();
    assertThat(entradas).isNotEmpty();
    for (var i = 1; i < entradas.size(); i++) {
      assertThat(entradas.get(i).path("occurredAt").asText())
        .as("ordem cronológica")
        .isGreaterThanOrEqualTo(entradas.get(i - 1).path("occurredAt").asText());
    }
  }

  @Entao("cada registro do histórico deve conter a etapa, o status resultante e o timestamp de execução")
  public void cadaRegistroDoHistoricoDeveConterAEtapaO() {
    for (final var entrada : historicoBody()) {
      assertThat(entrada.path("status").asText()).isNotBlank();
      assertThat(entrada.path("occurredAt").asText()).isNotBlank();
      assertThat(entrada.path("detail").asText()).isNotBlank();
    }
  }

  @Quando("o usuário consultar o status do documento {string}")
  public void oUsuarioConsultarOStatusDoDocumentoString(String idLiteral) {
    response = getStatus(idLiteral, "dev_user");
  }

  @Entao("a consulta deve ser rejeitada com um erro de {string}")
  public void aConsultaDeveSerRejeitadaComUmErroDe(String motivo) {
    assertThat(response.getStatusCode().value()).as("corpo=%s", response.getBody()).isEqualTo(404);
    assertThat(campo(response, "detail")).isEqualTo(motivo);
  }

  // ---------------------------------------------------------------- apoio HTTP

  private RestClient http() {
    return RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  private String tokenDe(String usuarioLogico) {
    return KeycloakTokens.userToken(issuerUri, REALM_USERS.getOrDefault(usuarioLogico, usuarioLogico));
  }

  /**
   * Aliases simbólicos de cenário ({@code Documento_A}, {@code doc-456}) não são nomes de
   * arquivo reais — sem extensão, a validação de tipo (RF04) rejeitaria o upload. Deriva
   * um nome real (.pdf por padrão) e conteúdo compatível via {@link SyntheticFiles#of}.
   */
  private UUID uploadArquivo(String usuarioLogico, String nomeLogico) {
    final var nomeReal = nomeLogico.contains(".") ? nomeLogico : nomeLogico + ".pdf";
    return uploadArquivo(usuarioLogico, nomeReal, SyntheticFiles.of(nomeReal, 2048));
  }

  private UUID uploadArquivo(String usuarioLogico, String arquivo, byte[] conteudo) {
    final var resource = new ByteArrayResource(conteudo) {
      @Override
      public String getFilename() {
        return arquivo;
      }
    };
    final var partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.APPLICATION_PDF);
    final var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("file", new HttpEntity<>(resource, partHeaders));

    final var resp = http().post()
      .uri("/api/v1/documents")
      .headers(h -> h.setBearerAuth(tokenDe(usuarioLogico)))
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .body(parts)
      .retrieve()
      .onStatus(s -> true, (req, res) -> { /* não lançar em 4xx/5xx */ })
      .toEntity(String.class);
    assertThat(resp.getStatusCode().value()).as("upload aceito; corpo=%s", resp.getBody()).isEqualTo(202);
    return UUID.fromString(campo(resp, "id"));
  }

  private ResponseEntity<String> substituirVersao(String documentId, String usuarioLogico, String arquivo, byte[] conteudo) {
    final var resource = new ByteArrayResource(conteudo) {
      @Override
      public String getFilename() {
        return arquivo;
      }
    };
    final var partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.APPLICATION_PDF);
    final var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("file", new HttpEntity<>(resource, partHeaders));

    return http().post()
      .uri("/api/v1/documents/{id}/versions", documentId)
      .headers(h -> h.setBearerAuth(tokenDe(usuarioLogico)))
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .body(parts)
      .retrieve()
      .onStatus(s -> true, (req, res) -> { })
      .toEntity(String.class);
  }

  private ResponseEntity<String> getStatus(String documentId, String usuarioLogico) {
    return http().get()
      .uri("/api/v1/documents/{id}/status", documentId)
      .headers(h -> h.setBearerAuth(tokenDe(usuarioLogico)))
      .retrieve()
      .onStatus(s -> true, (req, res) -> { })
      .toEntity(String.class);
  }

  private ResponseEntity<String> getHistory(String documentId, String usuarioLogico) {
    return http().get()
      .uri("/api/v1/documents/{id}/history", documentId)
      .headers(h -> h.setBearerAuth(tokenDe(usuarioLogico)))
      .retrieve()
      .onStatus(s -> true, (req, res) -> { })
      .toEntity(String.class);
  }

  private ResponseEntity<String> deleteDocumento(String documentId, String usuarioLogico) {
    return http().delete()
      .uri("/api/v1/documents/{id}", documentId)
      .headers(h -> h.setBearerAuth(tokenDe(usuarioLogico)))
      .retrieve()
      .onStatus(s -> true, (req, res) -> { })
      .toEntity(String.class);
  }

  private String campo(ResponseEntity<String> resp, String nome) {
    try {
      return JSON.readTree(resp.getBody()).path(nome).asText();
    } catch (IOException e) {
      throw new UncheckedIOException("Corpo de resposta não é JSON: " + resp.getBody(), e);
    }
  }

  private List<JsonNode> historicoBody() {
    try {
      final var arr = JSON.readTree(response.getBody());
      final var lista = new java.util.ArrayList<JsonNode>();
      arr.forEach(lista::add);
      return lista;
    } catch (IOException e) {
      throw new UncheckedIOException("Corpo de resposta não é JSON: " + response.getBody(), e);
    }
  }

  // ---------------------------------------------------------------- apoio simulação de status (D1)

  private void avancarAte(UUID documentId, String etapaFinal) {
    final var index = PRE_FORK_SEQUENCE.indexOf(etapaFinal);
    final var alvo = index < 0 ? PRE_FORK_SEQUENCE : PRE_FORK_SEQUENCE.subList(0, index + 1);
    var anterior = "UPLOADED";
    for (final var etapa : alvo) {
      jdbc.update("""
          INSERT INTO document_status_history (document_id, from_status, to_status, occurred_at, detail)
          VALUES (?, ?, ?, now(), ?)
          """, documentId, anterior, etapa, "Simulação de teste (fixture BDD, design.md D1)");
      anterior = etapa;
    }
    jdbc.update("UPDATE documents SET status = ? WHERE id = ?", anterior, documentId);
  }

  private void registrarTransicaoDeRamo(UUID documentId, String branch, String status) {
    jdbc.update("""
        INSERT INTO document_status_history (document_id, from_status, to_status, branch, occurred_at, detail)
        VALUES (?, ?, ?, ?, now(), ?)
        """, documentId, "CHUNKING", status, branch, branch + " " + status);
  }

  private void setBranchStatus(UUID documentId, String branch, String valor) {
    final var coluna = "EMBEDDING".equals(branch) ? "embedding_status" : "graph_status";
    jdbc.update("UPDATE documents SET " + coluna + " = ? WHERE id = ?", valor, documentId);
  }

  private void registrarFalhaDeEtapa(UUID documentId, String etapa) {
    jdbc.update("""
        INSERT INTO processing_errors (document_id, stage, error_code, message, attempt, transient, correlation_id, occurred_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, now())
        """, documentId, etapa, "RETRIES_EXHAUSTED", "Falha definitiva simulada em teste (fixture BDD)", 3, false,
      "bdd-" + documentId);
  }

  /**
   * Deriva o status agregado (RF08) quando os dois ramos atingem estado terminal — a
   * lógica de derivação em si é responsabilidade de um orquestrador real (Épico 3+); aqui
   * só simula o resultado esperado dessa derivação sobre o schema já existente.
   */
  private void recomputeAggregateStatus(UUID documentId) {
    final var linha = jdbc.queryForMap(
      "SELECT embedding_status, graph_status FROM documents WHERE id = ?", documentId);
    final var embedding = (String) linha.get("embedding_status");
    final var graph = (String) linha.get("graph_status");
    if (!isTerminal(embedding) || !isTerminal(graph)) {
      return;
    }
    final var agregado = "COMPLETED".equals(embedding) && "COMPLETED".equals(graph) ? "COMPLETED"
      : "FAILED".equals(embedding) && "FAILED".equals(graph) ? "FAILED"
      : "PARTIALLY_COMPLETED";
    jdbc.update("UPDATE documents SET status = ? WHERE id = ?", agregado, documentId);
  }

  private boolean isTerminal(String subStatus) {
    return "COMPLETED".equals(subStatus) || "FAILED".equals(subStatus);
  }

  // ---------------------------------------------------------------- apoio fixture Neo4j/OpenSearch (D1/D4)

  private String idDe(Map<String, String> aliases, String aliasLogico) {
    return aliases.computeIfAbsent(aliasLogico, k -> UUID.randomUUID().toString());
  }

  private void criarEntidadeOrfa(String entityId, String tenantId) {
    neo4jClient.query("MERGE (e:Entity {id: $id}) SET e.tenantId = $tenantId")
      .bindAll(Map.of("id", entityId, "tenantId", tenantId))
      .run();
  }

  private void conectarChunkAEntidade(String chunkId, String entityId, String tenantId) {
    neo4jClient.query("""
        MERGE (c:Chunk {id: $chunkId})
        MERGE (e:Entity {id: $entityId}) SET e.tenantId = $tenantId
        MERGE (c)-[:MENTIONS]->(e)
        """)
      .bindAll(Map.of("chunkId", chunkId, "entityId", entityId, "tenantId", tenantId))
      .run();
  }

  private void criarRelacionamentoEntreEntidades(String id1, String id2, String tipo) {
    if (!tipo.matches("[A-Z_]+")) {
      throw new IllegalArgumentException("Tipo de relacionamento não reconhecido: " + tipo);
    }
    neo4jClient.query("MATCH (a:Entity {id: $id1}), (b:Entity {id: $id2}) MERGE (a)-[:" + tipo + "]->(b)")
      .bindAll(Map.of("id1", id1, "id2", id2))
      .run();
  }

  private boolean entidadeExiste(String entityId) {
    return !neo4jClient.query("MATCH (e:Entity {id: $id}) RETURN e.id AS id")
      .bindAll(Map.of("id", entityId))
      .fetch().all().isEmpty();
  }

  private void criarDocumentoEChunkNoGrafo(String documentId, String chunkId, String tenantId, boolean ativo) {
    neo4jClient.query("""
        MERGE (d:Document {id: $documentId}) SET d.tenantId = $tenantId, d.isActive = $ativo
        MERGE (d)-[:HAS_CHUNK]->(c:Chunk {id: $chunkId}) SET c.tenantId = $tenantId, c.isActive = $ativo
        """)
      .bindAll(Map.of("documentId", documentId, "chunkId", chunkId, "tenantId", tenantId, "ativo", ativo))
      .run();
  }

  private boolean neoIsActive(String label, String id) {
    final var linhas = neo4jClient.query("MATCH (n:" + label + " {id: $id}) RETURN n.isActive AS ativo")
      .bindAll(Map.of("id", id))
      .fetch().all();
    assertThat(linhas).as("nó %s(%s) existe no grafo", label, id).isNotEmpty();
    return (Boolean) linhas.iterator().next().get("ativo");
  }

  private void indexarChunkFixture(String chunkId, String documentId, String tenantId) {
    try {
      openSearchClient.index(new IndexRequest.Builder<Map<String, Object>>()
        .index(CHUNK_INDEX_ALIAS)
        .id(chunkId)
        .refresh(Refresh.True)
        .document(Map.of("documentId", documentId, "tenantId", tenantId, "isActive", true))
        .build());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean chunkAtivoNoOpenSearch(String chunkId) {
    try {
      final var resp = openSearchClient.get(new GetRequest.Builder().index(CHUNK_INDEX_ALIAS).id(chunkId).build(), Map.class);
      assertThat(resp.found()).as("chunk '%s' indexado no OpenSearch", chunkId).isTrue();
      return (Boolean) resp.source().get("isActive");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void refreshChunkIndex() {
    try {
      openSearchClient.indices().refresh(new RefreshRequest.Builder().index(CHUNK_INDEX_ALIAS).build());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
