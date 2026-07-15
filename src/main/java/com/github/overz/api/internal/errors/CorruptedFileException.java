package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/**
 * RF02 + decisão D3 do change epico-1: falha no check estrutural barato (assinatura/trailer
 * do formato). Corrupção profunda que passe aqui é responsabilidade da extração (RF27).
 */
public final class CorruptedFileException extends UploadRejectedException {

  public static final String CODE = "CORRUPTED_FILE";

  public CorruptedFileException(final String reason) {
    super(CODE, "Arquivo corrompido: " + reason, HttpStatus.BAD_REQUEST);
  }

}
