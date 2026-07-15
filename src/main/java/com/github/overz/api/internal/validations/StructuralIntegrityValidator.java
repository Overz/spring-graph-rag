package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.CorruptedFileException;
import com.github.overz.shared.errors.ApplicationException;
import com.github.overz.shared.support.Bytes;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Validação nº 5 (RF02 + decisão D3 do change epico-1): check estrutural barato por
 * formato — PDF exige assinatura {@code %PDF} e trailer {@code %%EOF}; JPEG exige os
 * marcadores SOI/EOI; PNG exige a assinatura de 8 bytes e o chunk {@code IEND}.
 * Roda <em>depois</em> da validação de tipo: aqui o conteúdo já é do formato certo,
 * só pode estar truncado/ilegível. Corrupção profunda que passe aqui falha na
 * extração ({@code EXTRACTION_FAILED}, RF27).
 */
public final class StructuralIntegrityValidator implements UploadValidator {

  private static final int TRAILER_WINDOW = 1024;
  private static final byte[] PDF_SIGNATURE = "%PDF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PDF_TRAILER = "%%EOF".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] PNG_SIGNATURE =
    {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] PNG_IEND = "IEND".getBytes(StandardCharsets.US_ASCII);

  @Override
  public void validate(final UploadCandidate candidate) {
    try {
      switch (candidate.extension()) {
        case "pdf" -> validatePdf(candidate);
        case "jpg", "jpeg" -> validateJpeg(candidate);
        case "png" -> validatePng(candidate);
        default -> { /* demais formatos: sem estrutura binária barata a verificar */ }
      }
    } catch (IOException e) {
      throw new ApplicationException("Falha lendo conteúdo para verificação de integridade", e);
    }
  }

  private void validatePdf(final UploadCandidate candidate) throws IOException {
    if (!Bytes.startsWith(head(candidate, PDF_SIGNATURE.length), PDF_SIGNATURE)) {
      throw new CorruptedFileException("assinatura %PDF ausente");
    }
    if (!Bytes.contains(tail(candidate, TRAILER_WINDOW), PDF_TRAILER)) {
      throw new CorruptedFileException("trailer %%EOF ausente — conteúdo truncado ou ilegível");
    }
  }

  private void validateJpeg(final UploadCandidate candidate) throws IOException {
    final var head = head(candidate, 2);
    if (head.length < 2 || head[0] != (byte) 0xFF || head[1] != (byte) 0xD8) {
      throw new CorruptedFileException("marcador SOI (FFD8) ausente");
    }
    final var tail = tail(candidate, 2);
    if (tail.length < 2 || tail[0] != (byte) 0xFF || tail[1] != (byte) 0xD9) {
      throw new CorruptedFileException("marcador EOI (FFD9) ausente — conteúdo truncado");
    }
  }

  private void validatePng(final UploadCandidate candidate) throws IOException {
    if (!Bytes.startsWith(head(candidate, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
      throw new CorruptedFileException("assinatura PNG ausente");
    }
    if (!Bytes.contains(tail(candidate, 16), PNG_IEND)) {
      throw new CorruptedFileException("chunk IEND ausente — conteúdo truncado");
    }
  }

  private byte[] head(final UploadCandidate candidate, final int length) throws IOException {
    try (var input = Files.newInputStream(candidate.content())) {
      return input.readNBytes(length);
    }
  }

  private byte[] tail(final UploadCandidate candidate, final int window) throws IOException {
    try (var file = new RandomAccessFile(candidate.content().toFile(), "r")) {
      final var size = file.length();
      final var length = (int) Math.min(window, size);
      final var buffer = new byte[length];
      file.seek(size - length);
      file.readFully(buffer);
      return buffer;
    }
  }

}
