package com.github.overz.api.internal.errors;

import com.github.overz.shared.errors.HttpApplicationException;
import org.springframework.http.HttpStatusCode;

/**
 * Base das rejeições da cadeia de validação de upload (RF02): cada erro concreto carrega
 * um {@code code} estável — o contrato que os cenários BDD asseguram — exposto como
 * propriedade {@code code} do {@code ProblemDetail} (RFC 9457).
 */
public abstract class UploadRejectedException extends HttpApplicationException {

  private final String code;

  protected UploadRejectedException(final String code, final String message, final HttpStatusCode status) {
    super(message, null, status);
    this.code = code;
    getBody().setProperty("code", code);
  }

  public String code() {
    return code;
  }

}
