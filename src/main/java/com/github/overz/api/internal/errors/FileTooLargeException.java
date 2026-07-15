package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/**
 * RF03: arquivos individuais acima de 5MB são rejeitados; a mensagem orienta a
 * pré-divisão de documentos grandes (complemento do RF15).
 */
public final class FileTooLargeException extends UploadRejectedException {

  public static final String CODE = "FILE_TOO_LARGE";

  public FileTooLargeException() {
    super(CODE,
      "Tamanho máximo de 5MB excedido. Pré-divida documentos grandes (ex.: PDFs escaneados multipágina) antes do envio.",
      HttpStatus.PAYLOAD_TOO_LARGE);
  }

}
