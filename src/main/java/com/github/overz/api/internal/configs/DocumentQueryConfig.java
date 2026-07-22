package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.controllers.DocumentQueryController;
import com.github.overz.rag.DocumentCommandApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring do recurso de listagem (RF40): consulta paginada, compartilhada no tenant. */
@Configuration
class DocumentQueryConfig {

  @Bean
  DocumentQueryController documentQueryController(final DocumentCommandApi documents) {
    return new DocumentQueryController(documents);
  }

}
