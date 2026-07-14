package com.github.overz.api.internal.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link CallerContextArgumentResolver} no MVC.
 */
@Configuration
@RequiredArgsConstructor
class WebMvcSecurityConfig implements WebMvcConfigurer {

  private final CallerContextArgumentResolver callerContextArgumentResolver;

  @Override
  public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(callerContextArgumentResolver);
  }

}
