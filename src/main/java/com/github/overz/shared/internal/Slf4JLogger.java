package com.github.overz.shared.internal;

import com.github.overz.shared.Logger;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Slf4JLogger implements Logger {

  private final org.slf4j.Logger delegate;

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
