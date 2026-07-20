package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF35: token opaco inexistente, expirado ou já revogado. */
public final class InvalidPhantomTokenException extends AuthRejectedException {

  public static final String CODE = "INVALID_PHANTOM_TOKEN";

  public InvalidPhantomTokenException() {
    super(CODE, "Token inválido, expirado ou revogado.", HttpStatus.UNAUTHORIZED);
  }

}
