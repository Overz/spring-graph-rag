package com.github.overz.api.internal.controllers;

import com.github.overz.api.internal.dtos.DocumentHistoryEntryResponse;
import com.github.overz.api.internal.dtos.DocumentStatusResponse;
import com.github.overz.api.internal.dtos.UploadAcceptedResponse;
import com.github.overz.api.internal.errors.DocumentAccessDeniedException;
import com.github.overz.api.internal.errors.DocumentNotFoundException;
import com.github.overz.api.internal.mappers.DocumentLifecycleResponseMapper;
import com.github.overz.api.internal.mappers.UploadResponseMapper;
import com.github.overz.api.internal.services.DocumentUploadService;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.DocumentCommandOutcome;
import com.github.overz.shared.security.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * RF09/RF10 — consulta de status/histórico, exclusão lógica e substituição de versão.
 * Separado do {@link DocumentUploadController} (design.md D4): fluxo de aceite original
 * vs. gestão do ciclo de vida de um documento já existente.
 */
@RestController
@RequiredArgsConstructor
public class DocumentLifecycleController {

  private final DocumentCommandApi documents;
  private final DocumentUploadService documentUploadService;

  @GetMapping(path = { "/api/v1/documents/{id}/status" })
  DocumentStatusResponse status(@PathVariable final UUID id, final CallerContext caller) {
    final var status = documents.statusOf(id, caller.tenantId(), caller.ownerId())
      .orElseThrow(DocumentNotFoundException::new);
    return DocumentLifecycleResponseMapper.toResponse(status);
  }

  @GetMapping(path = { "/api/v1/documents/{id}/history" })
  List<DocumentHistoryEntryResponse> history(@PathVariable final UUID id, final CallerContext caller) {
    final var entries = documents.historyOf(id, caller.tenantId(), caller.ownerId())
      .orElseThrow(DocumentNotFoundException::new);
    return DocumentLifecycleResponseMapper.toResponse(entries);
  }

  @DeleteMapping(path = { "/api/v1/documents/{id}" })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable final UUID id, final CallerContext caller) {
    requireGranted(documents.deleteDocument(id, caller.tenantId(), caller.ownerId()));
  }

  @PostMapping(
    path = { "/api/v1/documents/{id}/versions" },
    consumes = { MediaType.MULTIPART_FORM_DATA_VALUE }
  )
  @ResponseStatus(HttpStatus.ACCEPTED)
  UploadAcceptedResponse replaceVersion(
    @PathVariable final UUID id,
    @RequestParam("file") final MultipartFile file,
    final CallerContext caller
  ) {
    final var result = documentUploadService.acceptReplacement(id, file, caller);
    requireGranted(result.outcome());
    return UploadResponseMapper.toResponse(result.replacement());
  }

  private void requireGranted(final DocumentCommandOutcome outcome) {
    switch (outcome) {
      case NOT_FOUND -> throw new DocumentNotFoundException();
      case FORBIDDEN -> throw new DocumentAccessDeniedException();
      case OK -> {
        // ignored
      }
    }
  }

}
