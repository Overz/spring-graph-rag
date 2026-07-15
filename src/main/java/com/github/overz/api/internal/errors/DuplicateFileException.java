package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF07: mesmo hash SHA-256 já enviado com sucesso pelo mesmo tenant+owner. */
public final class DuplicateFileException extends UploadRejectedException {

  public static final String CODE = "DUPLICATE_FILE";

  public DuplicateFileException() {
    super(CODE,
      "Arquivo duplicado: conteúdo idêntico já foi enviado por este usuário. "
        + "Use o comando explícito de reprocessamento para reexecutar o pipeline.",
      HttpStatus.CONFLICT);
  }

}
