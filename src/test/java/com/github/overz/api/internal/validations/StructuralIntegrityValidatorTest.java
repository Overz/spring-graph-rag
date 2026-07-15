package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.CorruptedFileException;
import com.github.overz.bdd.SyntheticFiles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class StructuralIntegrityValidatorTest extends Assertions {

  private final StructuralIntegrityValidator validator = new StructuralIntegrityValidator();

  @TempDir
  Path tempDir;

  @Test
  void aceitaPdfEstruturalmenteValido() throws Exception {
    final var content = tempDir.resolve("valido.pdf");
    Files.write(content, SyntheticFiles.of("valido.pdf", 1024));

    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().filename("valido.pdf").content(content).build()));
  }

  @Test
  void rejeitaPdfTruncado() throws Exception {
    final var content = tempDir.resolve("truncado.pdf");
    Files.write(content, SyntheticFiles.truncatedPdf());

    assertThrows(CorruptedFileException.class, () ->
      validator.validate(UploadCandidate.builder().filename("truncado.pdf").content(content).build()));
  }

}
