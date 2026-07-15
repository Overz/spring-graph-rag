package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF03 complemento: cota do tenant (armazenamento total ou nº de arquivos ativos) excedida. */
public final class QuotaExceededException extends UploadRejectedException {

  public static final String CODE = "QUOTA_EXCEEDED";

  private QuotaExceededException(final String message) {
    super(CODE, message, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  public static QuotaExceededException storage() {
    return new QuotaExceededException("Cota de armazenamento do tenant excedida");
  }

  public static QuotaExceededException activeFiles() {
    return new QuotaExceededException("Cota de arquivos ativos do tenant excedida");
  }

}
