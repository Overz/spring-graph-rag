package com.github.overz.shared;


import com.github.overz.shared.internal.Slf4JILogger;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

/**
 * Fábrica de {@link ILogger}, uma por classe. Cache key=classe, value=instância SLF4J —
 * {@code LoggerFactory.getLogger(...)} nunca deve ser chamado direto fora daqui.
 */
public final class LoggerFactory {

  private static final ClassValue<Logger> logger = new ClassValue<>() {
    @Override
    protected Logger computeValue(final @NonNull Class<?> type) {
      return org.slf4j.LoggerFactory.getLogger(type);
    }
  };

  private LoggerFactory() {
  }

  public static ILogger of(final Class<?> owner) {
    return new Slf4JILogger(logger.get(owner));
  }

}
