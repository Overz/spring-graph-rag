package com.github.overz.api.internal.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * Parâmetros de negócio da validação de upload (RF02/RF03/RF04), externalizados em
 * {@code app.upload.*} no {@code application.yaml} — nada disso é invariante de formato
 * (essas ficam hardcoded nos próprios validadores), é configuração de negócio.
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
  long maxFileSizeBytes,
  int maxFilenameLength,
  Map<FileType, Set<String>> acceptedTypes
) {
}
