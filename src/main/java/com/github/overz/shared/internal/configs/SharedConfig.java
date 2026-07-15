package com.github.overz.shared.internal.configs;

import com.github.overz.shared.internal.FileSystemDocumentStorage;
import com.github.overz.shared.storage.DocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central dos beans do módulo {@code shared}: as implementações não levam
 * estereótipo ({@code @Component}) — o wiring inteiro do módulo é visível aqui.
 */
@Configuration
class SharedConfig {

  @Bean
  DocumentStorage documentStorage(@Value("${app.storage.base-dir}") final String baseDir) {
    return new FileSystemDocumentStorage(baseDir);
  }

}
