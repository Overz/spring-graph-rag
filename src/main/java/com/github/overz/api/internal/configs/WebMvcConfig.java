package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.security.CallerContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link CallerContextArgumentResolver} no MVC.
 */
@Configuration
class WebMvcConfig implements WebMvcConfigurer {

  @Bean
  CallerContextArgumentResolver callerContextArgumentResolver() {
    return new CallerContextArgumentResolver();
  }

  @Override
  public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(callerContextArgumentResolver());
  }

}
