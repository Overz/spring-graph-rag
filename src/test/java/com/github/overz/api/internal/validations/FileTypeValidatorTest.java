package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.FileType;
import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.MimeMismatchException;
import com.github.overz.api.internal.errors.UnsupportedFileTypeException;
import com.github.overz.bdd.SyntheticFiles;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

class FileTypeValidatorTest extends Assertions {

  @TempDir
  Path tempDir;

  @Test
  void aceitaExtensaoEConteudoCoerentes() throws Exception {
    final var content = tempDir.resolve("documento.pdf");
    Files.write(content, SyntheticFiles.of("documento.pdf", 1024));
    final var validator = new FileTypeValidator(new UploadProperties(0, 255, Map.of(FileType.PDF, Set.of("application/pdf"))));

    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().filename("documento.pdf").content(content).build()));
  }

  @Test
  void rejeitaExtensaoForaDaListaAceita() throws Exception {
    final var content = tempDir.resolve("documento.exe");
    Files.write(content, SyntheticFiles.windowsExecutable());
    final var validator = new FileTypeValidator(new UploadProperties(0, 255, Map.of(FileType.PDF, Set.of("application/pdf"))));

    assertThrows(UnsupportedFileTypeException.class, () ->
      validator.validate(UploadCandidate.builder().filename("documento.exe").content(content).build()));
  }

  @Test
  void rejeitaConteudoQueNaoCorrespondeAExtensao() throws Exception {
    final var content = tempDir.resolve("disfarcado.pdf");
    Files.write(content, SyntheticFiles.of("qualquer.png", 1024));
    final var validator = new FileTypeValidator(new UploadProperties(0, 255, Map.of(
      FileType.PDF, Set.of("application/pdf"),
      FileType.PNG, Set.of("image/png")
    )));

    assertThrows(MimeMismatchException.class, () ->
      validator.validate(UploadCandidate.builder().filename("disfarcado.pdf").content(content).build()));
  }

}
