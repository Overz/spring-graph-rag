package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.controllers.DocumentUploadController;
import com.github.overz.api.internal.services.DocumentUploadService;
import com.github.overz.api.internal.validations.DuplicateFileValidator;
import com.github.overz.api.internal.validations.EicarSignatureMalwareScanner;
import com.github.overz.api.internal.validations.EmptyFileValidator;
import com.github.overz.api.internal.validations.FileSizeValidator;
import com.github.overz.api.internal.validations.FileTypeValidator;
import com.github.overz.api.internal.validations.FilenameValidator;
import com.github.overz.api.internal.validations.MalwareScanner;
import com.github.overz.api.internal.validations.MalwareValidator;
import com.github.overz.api.internal.validations.QuotaValidator;
import com.github.overz.api.internal.validations.StructuralIntegrityValidator;
import com.github.overz.api.internal.validations.UploadValidator;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.shared.storage.DocumentStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wiring do recurso de upload (RF01–RF07): nenhuma classe do fluxo leva estereótipo —
 * a ordem da cadeia de validação (do mais barato ao mais caro, {@code sdd/ingestao.md}
 * §2) fica explícita na lista montada aqui, no lugar de {@code @Order} espalhado.
 */
@Configuration
class UploadConfig {

  @Bean
  MalwareScanner malwareScanner() {
    return new EicarSignatureMalwareScanner();
  }

  @Bean
  List<UploadValidator> uploadValidators(
    final UploadProperties properties,
    final MalwareScanner malwareScanner,
    final DocumentCommandApi documents
  ) {
    return List.of(
      new FileSizeValidator(properties),
      new FilenameValidator(properties),
      new EmptyFileValidator(),
      new FileTypeValidator(properties),
      new StructuralIntegrityValidator(),
      new DuplicateFileValidator(documents),
      new QuotaValidator(documents),
      new MalwareValidator(malwareScanner)
    );
  }

  /**
   * Cadeia de {@code POST /{id}/versions} (RF10 complemento): igual à de aceite original,
   * exceto {@link DuplicateFileValidator} — decisão de reenviar conteúdo já usado antes
   * (ex.: reverter uma versão errada) é do usuário chamando o endpoint explícito de
   * versionamento, não do sistema bloquear (confirmado com o usuário).
   */
  @Bean
  List<UploadValidator> versionReplacementValidators(
    final UploadProperties properties,
    final MalwareScanner malwareScanner,
    final DocumentCommandApi documents
  ) {
    return List.of(
      new FileSizeValidator(properties),
      new FilenameValidator(properties),
      new EmptyFileValidator(),
      new FileTypeValidator(properties),
      new StructuralIntegrityValidator(),
      new QuotaValidator(documents),
      new MalwareValidator(malwareScanner)
    );
  }

  @Bean
  DocumentUploadService documentUploadService(
    final List<UploadValidator> uploadValidators,
    final List<UploadValidator> versionReplacementValidators,
    final DocumentStorage storage,
    final DocumentCommandApi documents
  ) {
    return new DocumentUploadService(uploadValidators, versionReplacementValidators, storage, documents);
  }

  @Bean
  DocumentUploadController documentUploadController(final DocumentUploadService documentUploadService) {
    return new DocumentUploadController(documentUploadService);
  }

}
