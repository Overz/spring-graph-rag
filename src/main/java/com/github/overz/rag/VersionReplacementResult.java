package com.github.overz.rag;

import java.io.Serializable;

/**
 * Resultado da substituição de versão (RF10 complemento): {@code replacement} só é
 * preenchido quando {@code outcome} é {@link DocumentCommandOutcome#OK}.
 */
public record VersionReplacementResult(
  DocumentCommandOutcome outcome,
  RegisteredDocument replacement
) implements Serializable {

  public static VersionReplacementResult ok(final RegisteredDocument replacement) {
    return new VersionReplacementResult(DocumentCommandOutcome.OK, replacement);
  }

  public static VersionReplacementResult notFound() {
    return new VersionReplacementResult(DocumentCommandOutcome.NOT_FOUND, null);
  }

  public static VersionReplacementResult forbidden() {
    return new VersionReplacementResult(DocumentCommandOutcome.FORBIDDEN, null);
  }

}
