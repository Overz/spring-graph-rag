package com.github.overz.api.internal.errors;

import com.github.overz.shared.errors.HttpApplicationException;
import org.springframework.http.HttpStatusCode;

/** Base das rejeições de status/histórico/exclusão/versionamento (RF09/RF10/RF30). */
public abstract class DocumentLifecycleRejectedException extends HttpApplicationException {

  private final String code;

  protected DocumentLifecycleRejectedException(final String code, final String message, final HttpStatusCode status) {
    super(message, null, status);
    this.code = code;
    getBody().setProperty("code", code);
  }

  public String code() {
    return code;
  }

}
