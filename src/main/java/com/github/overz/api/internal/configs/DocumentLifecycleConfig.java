package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.controllers.DocumentLifecycleController;
import com.github.overz.api.internal.services.DocumentUploadService;
import com.github.overz.rag.DocumentCommandApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring do recurso de ciclo de vida (RF09/RF10): consulta, exclusão, versionamento. */
@Configuration
class DocumentLifecycleConfig {

  @Bean
  DocumentLifecycleController documentLifecycleController(
    final DocumentCommandApi documents,
    final DocumentUploadService documentUploadService
  ) {
    return new DocumentLifecycleController(documents, documentUploadService);
  }

}
