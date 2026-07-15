package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/**
 * RF02: extensão e MIME real (detectado por conteúdo, Tika) são ambos suportados,
 * mas incoerentes entre si (ex.: conteúdo PNG num arquivo {@code .pdf}).
 */
public final class MimeMismatchException extends UploadRejectedException {

  public static final String CODE = "MIME_MISMATCH";

  public MimeMismatchException(final String extension, final String detected) {
    super(CODE,
      "Tipo MIME não corresponde à extensão: extensão '%s', conteúdo '%s'".formatted(extension, detected),
      HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

}
