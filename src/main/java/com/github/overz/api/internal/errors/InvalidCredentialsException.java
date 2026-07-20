package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF35: usuário ou senha incorretos no login. */
public final class InvalidCredentialsException extends AuthRejectedException {

  public static final String CODE = "INVALID_CREDENTIALS";

  public InvalidCredentialsException() {
    super(CODE, "Usuário ou senha inválidos.", HttpStatus.UNAUTHORIZED);
  }

}
