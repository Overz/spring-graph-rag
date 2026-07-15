package com.github.overz.shared.internal;

import com.github.overz.shared.errors.ApplicationException;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import com.github.overz.shared.storage.DocumentStorage;
import com.github.overz.shared.storage.StorageLocation;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Adaptador POSIX da porta {@link DocumentStorage} (ADR-001): o diretório-base
 * ({@code app.storage.base-dir}) é o mount JuiceFS em dev e um diretório temporário
 * nos testes — o adaptador não sabe a diferença. Gravação atômica: escreve num
 * arquivo temporário do mesmo filesystem e move para o destino final.
 *
 * <p>Registrado como bean em {@code SharedConfig} — sem estereótipo de classe,
 * conforme a convenção de configuração centralizada do projeto.
 */
@RequiredArgsConstructor
public final class FileSystemDocumentStorage implements DocumentStorage {

  private static final ILogger log = LoggerFactory.of(FileSystemDocumentStorage.class);

  private final Path baseDir;

  @Override
  public String store(final StorageLocation location, final InputStream content) {
    var target = baseDir;
    for (final var segment : location.segments()) {
      target = target.resolve(segment);
    }
    try {
      Files.createDirectories(target.getParent());
      final var temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      try {
        Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new ApplicationException("Falha gravando artefato no storage na chave " + location.key(), e);
    }
    log.debug("Artefato gravado no storage: key='{}'", location.key());
    return location.key();
  }

}
