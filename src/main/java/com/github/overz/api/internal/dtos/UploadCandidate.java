package com.github.overz.api.internal.dtos;

import com.github.overz.shared.security.CallerContext;
import lombok.Builder;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Upload em validação: o conteúdo já está num arquivo temporário (uma única passada de
 * streaming com hash — D9) e os validadores leem dele, nunca do multipart.
 *
 * @param filename            nome original enviado
 * @param declaredContentType Content-Type declarado na parte multipart (informativo —
 *                            a validação usa o MIME real por conteúdo, RF02)
 * @param sizeBytes           tamanho real gravado no temporário
 * @param sha256              hash SHA-256 do conteúdo, hex minúsculo (RF07)
 * @param content             arquivo temporário com o conteúdo
 * @param caller              identidade do chamador (claims — RF30)
 */
@Builder
public record UploadCandidate(
  String filename,
  String declaredContentType,
  long sizeBytes,
  String sha256,
  Path content,
  CallerContext caller
) implements Serializable {

  /**
   * Extensão em minúsculas, sem ponto; vazia quando não há.
   */
  public String extension() {
    if (filename == null) {
      return "";
    }
    final var dot = filename.lastIndexOf('.');
    return dot < 0 || dot == filename.length() - 1
      ? ""
      : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

}
