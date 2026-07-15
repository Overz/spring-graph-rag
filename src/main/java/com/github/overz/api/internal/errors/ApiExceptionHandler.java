package com.github.overz.api.internal.errors;

import com.github.overz.shared.errors.HttpApplicationException;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Único {@code @RestControllerAdvice} do módulo {@code api} (convenção do projeto):
 * renderiza todo {@link HttpApplicationException} como {@code ProblemDetail} (RFC 9457)
 * com o {@code code} estável que os cenários BDD asseguram.
 */
@RestControllerAdvice
class ApiExceptionHandler {

  private static final ILogger log = LoggerFactory.of(ApiExceptionHandler.class);

  @ExceptionHandler(HttpApplicationException.class)
  ResponseEntity<ProblemDetail> handleApplicationException(final HttpApplicationException error) {
    return render(error);
  }

  /**
   * O limite multipart do container (6MB) fica acima do limite de negócio (5MB) para a
   * validação de domínio responder primeiro; quando o container estoura antes (arquivo
   * muito acima), a resposta é a MESMA da validação — {@code FILE_TOO_LARGE}, não um
   * erro genérico (SDD ingestao §2).
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> handleMultipartLimit(final MaxUploadSizeExceededException e) {
    return render(new FileTooLargeException());
  }

  private ResponseEntity<ProblemDetail> render(final HttpApplicationException error) {
    log.warn("Requisição rejeitada: status={} detail='{}'", error.getStatusCode().value(), error.getMessage());
    return ResponseEntity.status(error.getStatusCode())
      .headers(error.getHeaders())
      .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body(error.getBody());
  }

}
