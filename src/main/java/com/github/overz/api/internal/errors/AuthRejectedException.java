package com.github.overz.api.internal.errors;

import com.github.overz.shared.errors.HttpApplicationException;
import org.springframework.http.HttpStatusCode;

/** Base das rejeições do fluxo de autenticação (RF35, ADR-004) — mesmo padrão de {@code code} do {@link UploadRejectedException}. */
public abstract class AuthRejectedException extends HttpApplicationException {

  private final String code;

  protected AuthRejectedException(final String code, final String message, final HttpStatusCode status) {
    super(message, null, status);
    this.code = code;
    getBody().setProperty("code", code);
  }

  public String code() {
    return code;
  }

}
