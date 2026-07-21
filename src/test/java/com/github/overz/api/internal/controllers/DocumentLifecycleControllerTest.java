package com.github.overz.api.internal.controllers;

import com.github.overz.api.internal.errors.DocumentAccessDeniedException;
import com.github.overz.api.internal.errors.DocumentNotFoundException;
import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.DocumentCommandOutcome;
import com.github.overz.rag.DocumentHistoryEntry;
import com.github.overz.rag.DocumentStatus;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.TenantQuota;
import com.github.overz.rag.TenantUsage;
import com.github.overz.rag.VersionReplacementResult;
import com.github.overz.shared.security.CallerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Só {@code status}/{@code history}/{@code delete} são testáveis sem infraestrutura real:
 * {@code replaceVersion} depende de {@link com.github.overz.api.internal.services.DocumentUploadService}
 * (validação + storage), sem porta própria — coberto pelos cenários de BDD.
 */
class DocumentLifecycleControllerTest extends Assertions {

  private final CallerContext caller = new CallerContext("tenant-1", "owner-1", Set.of());
  private final UUID documentId = UUID.randomUUID();

  @Test
  void statusDevolveORecursoQuandoDocumentoVisivel() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(Optional.of(DocumentStatus.EXTRACTING), null, null), null);

    final var response = controller.status(documentId, caller);

    assertEquals("EXTRACTING", response.status());
  }

  @Test
  void statusLancaNaoEncontradoQuandoDocumentoInvisivel() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(Optional.empty(), null, null), null);

    assertThrows(DocumentNotFoundException.class, () -> controller.status(documentId, caller));
  }

  @Test
  void historyDevolveEntradasQuandoDocumentoVisivel() {
    final var entradas = List.of(new DocumentHistoryEntry(DocumentStatus.UPLOADED, OffsetDateTime.now(), "detalhe"));
    final var controller = new DocumentLifecycleController(
      documentCommandApi(null, Optional.of(entradas), null), null);

    assertEquals(1, controller.history(documentId, caller).size());
  }

  @Test
  void historyLancaNaoEncontradoQuandoDocumentoInvisivel() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(null, Optional.empty(), null), null);

    assertThrows(DocumentNotFoundException.class, () -> controller.history(documentId, caller));
  }

  @Test
  void deleteNaoLancaQuandoOperacaoConcedida() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(null, null, DocumentCommandOutcome.OK), null);

    assertDoesNotThrow(() -> controller.delete(documentId, caller));
  }

  @Test
  void deleteLancaNaoEncontradoQuandoDocumentoNaoExiste() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(null, null, DocumentCommandOutcome.NOT_FOUND), null);

    assertThrows(DocumentNotFoundException.class, () -> controller.delete(documentId, caller));
  }

  @Test
  void deleteLancaAcessoNegadoQuandoDonoDiferente() {
    final var controller = new DocumentLifecycleController(
      documentCommandApi(null, null, DocumentCommandOutcome.FORBIDDEN), null);

    assertThrows(DocumentAccessDeniedException.class, () -> controller.delete(documentId, caller));
  }

  private DocumentCommandApi documentCommandApi(
    final Optional<DocumentStatus> status,
    final Optional<List<DocumentHistoryEntry>> history,
    final DocumentCommandOutcome deleteOutcome
  ) {
    return new DocumentCommandApi() {
      @Override
      public RegisteredDocument registerAcceptedUpload(final AcceptedUpload upload) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean successfulDuplicateExists(final String tenantId, final String ownerId, final String sha256) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<TenantQuota> quotaOf(final String tenantId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public TenantUsage usageOf(final String tenantId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<DocumentStatus> statusOf(final UUID id, final String tenantId, final String ownerId) {
        return status;
      }

      @Override
      public Optional<List<DocumentHistoryEntry>> historyOf(final UUID id, final String tenantId, final String ownerId) {
        return history;
      }

      @Override
      public DocumentCommandOutcome deleteDocument(final UUID id, final String tenantId, final String ownerId) {
        return deleteOutcome;
      }

      @Override
      public VersionReplacementResult replaceVersion(final UUID previousDocumentId, final AcceptedUpload newUpload) {
        throw new UnsupportedOperationException();
      }
    };
  }

}
