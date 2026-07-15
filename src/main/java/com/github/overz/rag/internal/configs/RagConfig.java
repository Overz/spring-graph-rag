package com.github.overz.rag.internal.configs;

import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.internal.repositories.DocumentRepository;
import com.github.overz.rag.internal.repositories.DocumentStatusHistoryRepository;
import com.github.overz.rag.internal.repositories.TenantQuotaRepository;
import com.github.overz.rag.internal.services.DocumentIngestService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração central dos beans do módulo {@code rag}: os services não levam
 * estereótipo — o wiring inteiro do módulo é visível aqui. (Os repositórios são
 * interfaces Spring Data, registradas pela autoconfiguração de JPA.)
 */
@Configuration
class RagConfig {

  @Bean
  DocumentCommandApi documentCommandApi(
    final DocumentRepository documents,
    final DocumentStatusHistoryRepository history,
    final TenantQuotaRepository quotas
  ) {
    return new DocumentIngestService(documents, history, quotas);
  }

}
