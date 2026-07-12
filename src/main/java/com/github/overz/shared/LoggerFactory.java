package com.github.overz.shared;


import com.github.overz.shared.internal.Slf4JLogger;

/**
 * Fábrica de {@link Logger}, uma por classe. Cache key=classe, value=instância SLF4J —
 * {@code LoggerFactory.getLogger(...)} nunca deve ser chamado direto fora daqui.
 */
public final class LoggerFactory {

  private static final ClassValue<org.slf4j.Logger> logger = new ClassValue<>() {
    @Override
    protected org.slf4j.Logger computeValue(final Class<?> type) {
      return org.slf4j.LoggerFactory.getLogger(type);
    }
  };

  private LoggerFactory() {
  }

  public static Logger of(final Class<?> owner) {
    return new Slf4JLogger(logger.get(owner));
  }

}
