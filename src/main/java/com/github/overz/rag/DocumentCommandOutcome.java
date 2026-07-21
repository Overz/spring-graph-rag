package com.github.overz.rag;

/**
 * Resultado de um comando restrito ao dono do documento (RF30): {@code rag} não depende
 * de {@code shared::errors} (`package-info.java`), então o mapeamento pra HTTP (404/403)
 * é responsabilidade do {@code api}, não deste módulo.
 */
public enum DocumentCommandOutcome {

  OK,
  NOT_FOUND,
  FORBIDDEN

}
