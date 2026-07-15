package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF04: extensão ou MIME real fora da lista suportada (PDF, JPG, JPEG, PNG, CSV, JSON, XML, TXT, MD). */
public final class UnsupportedFileTypeException extends UploadRejectedException {

  public static final String CODE = "UNSUPPORTED_FILE_TYPE";

  public UnsupportedFileTypeException(final String detected) {
    super(CODE, "Tipo MIME não suportado: " + detected, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

}
