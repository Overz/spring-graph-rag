package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/**
 * RF09/RF30: documento inexistente, de outro tenant ou (nas consultas de status/histórico)
 * de outro dono do mesmo tenant — as três causas respondem igual, sem expor o motivo real.
 */
public final class DocumentNotFoundException extends DocumentLifecycleRejectedException {

  public static final String CODE = "DOCUMENT_NOT_FOUND";

  public DocumentNotFoundException() {
    super(CODE, "Documento não encontrado", HttpStatus.NOT_FOUND);
  }

}
