package com.github.overz.shared.internal;

import com.github.overz.shared.logging.ILogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

@RequiredArgsConstructor
public final class Slf4JLogger implements ILogger {

  private final Logger delegate;

  @Override
  public void debug(final String message, final Object... args) {
    delegate.debug(message, args);
  }

  @Override
  public void info(final String message, final Object... args) {
    delegate.info(message, args);
  }

  @Override
  public void warn(final String message, final Object... args) {
    delegate.warn(message, args);
  }

  @Override
  public void error(final String message, final Object... args) {
    delegate.error(message, args);
  }
}
