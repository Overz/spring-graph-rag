package com.github.overz.shared.errors;

/**
 * Raiz de toda exceção lançada intencionalmente pela aplicação. Erros concretos se
 * agrupam por domínio via pacote/módulo e nome da classe (ex.: erros de upload em
 * {@code api}, erros de parsing em {@code rag}) — não por uma subclasse intermediária
 * forçada entre módulos. Ver CLAUDE.md, seção "Error Hierarchy (by Domain)".
 */
public sealed class ApplicationException extends RuntimeException permits HttpApplicationException {

  public ApplicationException(final String message) {
    this(message, null);
  }

  public ApplicationException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
