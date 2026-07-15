package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.MimeMismatchException;
import com.github.overz.api.internal.errors.UnsupportedFileTypeException;
import com.github.overz.shared.errors.ApplicationException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validação nº 4 (RF02/RF04): MIME real detectado por <em>conteúdo</em> (Tika, com o nome
 * como dica secundária — magic bytes têm prioridade), nunca pela extensão. Extensão fora
 * da lista de {@code app.upload.accepted-types} ou conteúdo não suportado →
 * {@code UNSUPPORTED_FILE_TYPE}; extensão e conteúdo suportados mas incoerentes →
 * {@code MIME_MISMATCH}. Roda <em>antes</em> do check estrutural: conteúdo de outro
 * formato é problema de tipo, não de corrupção.
 */
@RequiredArgsConstructor
public final class FileTypeValidator implements UploadValidator {

  private final UploadProperties properties;
  private final TikaConfig tika = TikaConfig.getDefaultConfig();

  @Override
  public void validate(final UploadCandidate candidate) {
    final var extension = candidate.extension();
    final var acceptedMimes = properties.acceptedTypes().get(extension);
    if (acceptedMimes == null) {
      throw new UnsupportedFileTypeException("extensão '." + extension + "' não é aceita");
    }

    final var detected = detect(candidate);
    if (acceptedMimes.contains(detected)) {
      return;
    }
    if (allAccepted().contains(detected)) {
      throw new MimeMismatchException(extension, detected);
    }
    throw new UnsupportedFileTypeException(detected);
  }

  private Set<String> allAccepted() {
    return properties.acceptedTypes().values().stream()
      .flatMap(Set::stream)
      .collect(Collectors.toUnmodifiableSet());
  }

  private String detect(final UploadCandidate candidate) {
    final var metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, candidate.filename());
    try (var stream = TikaInputStream.get(candidate.content(), metadata)) {
      return tika.getDetector().detect(stream, metadata).getBaseType().toString();
    } catch (IOException e) {
      throw new ApplicationException("Falha detectando o tipo MIME real do conteúdo", e);
    }
  }

}
