package com.github.overz.shared;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

import java.util.Objects;

public abstract non-sealed class HttpApplicationError extends ApplicationError implements ErrorResponse {

  private final HttpStatusCode httpStatusCode;
  private final HttpHeaders headers;
  private final ProblemDetail body;

  protected HttpApplicationError(final String message) {
    this(Objects.requireNonNull(message, "message"), null);
  }

  protected HttpApplicationError(final String message, final Throwable cause) {
    this(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  protected HttpApplicationError(
    final String message,
    final Throwable cause,
    final HttpStatusCode httpStatusCode
  ) {
    this(message, cause, httpStatusCode, HttpHeaders.EMPTY);
  }

  protected HttpApplicationError(
    final String message,
    final Throwable cause,
    final HttpStatusCode httpStatusCode,
    final HttpHeaders headers
  ) {
    this.httpStatusCode = Objects.requireNonNull(httpStatusCode, "httpStatusCode");
    this.headers = Objects.requireNonNull(headers, "headers");
    this.body = ProblemDetail.forStatusAndDetail(httpStatusCode, message);
    super(message, cause);
  }

  @Override
  public HttpStatusCode getStatusCode() {
    return this.httpStatusCode;
  }

  @Override
  public ProblemDetail getBody() {
    return body;
  }

  @Override
  public HttpHeaders getHeaders() {
    return this.headers;
  }

}
