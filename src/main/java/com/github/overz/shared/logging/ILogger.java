package com.github.overz.shared.logging;

/**
 * Interface de log que toda classe usa — ver {@link LoggerFactory#of(Class)}. Centraliza
 * a decisão de "como logar" num único lugar, trocável sem tocar quem consome.
 */
public interface ILogger {

  void debug(String message, Object... args);

  void info(String message, Object... args);

  void warn(String message, Object... args);

  void error(String message, Object... args);

}
