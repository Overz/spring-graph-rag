package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF30: documento existe no tenant do chamador, mas pertence a outro dono. */
public final class DocumentAccessDeniedException extends DocumentLifecycleRejectedException {

  public static final String CODE = "DOCUMENT_ACCESS_DENIED";

  public DocumentAccessDeniedException() {
    super(CODE, "Operação restrita ao dono do documento", HttpStatus.FORBIDDEN);
  }

}
