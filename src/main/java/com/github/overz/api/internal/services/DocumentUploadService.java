package com.github.overz.api.internal.services;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.UploadRejectedException;
import com.github.overz.api.internal.validations.UploadValidator;
import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.VersionReplacementResult;
import com.github.overz.shared.errors.ApplicationException;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import com.github.overz.shared.security.CallerContext;
import com.github.overz.shared.storage.DocumentStorage;
import com.github.overz.shared.storage.StorageLocation;
import com.github.overz.shared.storage.StorageStage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Fluxo do aceite de upload (SDD ingestao §§2/4, D9): multipart → arquivo temporário com
 * SHA-256 em streaming (uma passada) → cadeia de validação em ordem → storage RAW →
 * registro no {@code rag}. Storage <em>antes</em> da linha em {@code documents}: arquivo
 * órfão é inofensivo; linha sem arquivo quebraria o pipeline.
 */
@RequiredArgsConstructor
public class DocumentUploadService {

  private static final ILogger log = LoggerFactory.of(DocumentUploadService.class);

  private final List<UploadValidator> validators;
  private final DocumentStorage storage;
  private final DocumentCommandApi documents;

  public RegisteredDocument accept(final MultipartFile file, final CallerContext caller) {
    return documents.registerAcceptedUpload(validateAndStore(file, caller));
  }

  /**
   * Substituição de versão (RF10 complemento): mesma cadeia de validação/storage do
   * aceite original — inclusive {@code DuplicateFileValidator}/{@code QuotaValidator},
   * que continuam corretos aqui (RF07 é por hash de tenant+owner, não por documento
   * específico) — só o comando final ao {@code rag} muda.
   */
  public VersionReplacementResult acceptReplacement(
    final UUID previousDocumentId, final MultipartFile file, final CallerContext caller
  ) {
    return documents.replaceVersion(previousDocumentId, validateAndStore(file, caller));
  }

  private AcceptedUpload validateAndStore(final MultipartFile file, final CallerContext caller) {
    // Gerado no ato do upload (RF28 complemento) — acompanha o documento pipeline afora.
    final var correlationId = UUID.randomUUID().toString();
    final var candidate = spool(file, caller);
    try {
      runValidations(candidate, correlationId);

      final var documentId = UUID.randomUUID();
      final var location = new StorageLocation(
        StorageStage.RAW, caller.tenantId(), caller.ownerId(), documentId.toString(), candidate.filename());
      final String rawStorageKey;
      try (var content = Files.newInputStream(candidate.content())) {
        rawStorageKey = storage.store(location, content);
      } catch (IOException e) {
        throw new ApplicationException("Falha lendo o conteúdo temporário do upload", e);
      }

      return new AcceptedUpload(
        documentId,
        caller.tenantId(),
        caller.ownerId(),
        candidate.filename(),
        candidate.extension(),
        candidate.declaredContentType(),
        candidate.sizeBytes(),
        candidate.sha256(),
        rawStorageKey,
        correlationId
      );
    } finally {
      deleteQuietly(candidate.content());
    }
  }

  private void runValidations(final UploadCandidate candidate, final String correlationId) {
    try {
      validators.forEach(validator -> validator.validate(candidate));
    } catch (UploadRejectedException rejection) {
      // Rejeição não persiste linha (SDD ingestao §2) — o rastro fica no log estruturado.
      log.warn("Upload rejeitado: filename='{}' code='{}' motivo='{}' tenant='{}' correlationId='{}'",
        candidate.filename(), rejection.code(), rejection.getMessage(),
        candidate.caller().tenantId(), correlationId);
      throw rejection;
    }
  }

  /**
   * Uma única passada no multipart: hash + gravação em temporário (D9); os validadores
   * leem do temporário, nunca do multipart.
   */
  private UploadCandidate spool(final MultipartFile file, final CallerContext caller) {
    final MessageDigest sha256;
    try {
      sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new ApplicationException("SHA-256 indisponível no runtime", e);
    }
    try {
      final var temp = Files.createTempFile("upload-", ".spool");
      try (var input = new DigestInputStream(file.getInputStream(), sha256)) {
        Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        deleteQuietly(temp);
        throw e;
      }
      return UploadCandidate.builder()
        .filename(file.getOriginalFilename())
        .declaredContentType(file.getContentType())
        .sizeBytes(Files.size(temp))
        .sha256(HexFormat.of().formatHex(sha256.digest()))
        .content(temp)
        .caller(caller)
        .build();
    } catch (IOException e) {
      throw new ApplicationException("Falha recebendo o conteúdo do upload", e);
    }
  }

  private void deleteQuietly(final Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Falha removendo temporário de upload '{}': {}", path, e.getMessage());
    }
  }

}
