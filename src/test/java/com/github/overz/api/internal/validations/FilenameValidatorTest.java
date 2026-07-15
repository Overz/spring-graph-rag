package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.InvalidFilenameException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class FilenameValidatorTest extends Assertions {

  private final FilenameValidator validator = new FilenameValidator(new UploadProperties(0, 255, Map.of()));

  @Test
  void rejeitaNomeVazio() {
    assertThrows(InvalidFilenameException.class, () ->
      validator.validate(UploadCandidate.builder().filename("").build()));
  }

  @Test
  void rejeitaNomeMuitoLongo() {
    final var validadorComLimiteBaixo = new FilenameValidator(new UploadProperties(0, 10, Map.of()));
    assertThrows(InvalidFilenameException.class, () ->
      validadorComLimiteBaixo.validate(UploadCandidate.builder().filename("a".repeat(11)).build()));
  }

  @Test
  void rejeitaPathTraversal() {
    assertThrows(InvalidFilenameException.class, () ->
      validator.validate(UploadCandidate.builder().filename("../etc/passwd").build()));
  }

  @Test
  void rejeitaCaractereDeControle() {
    assertThrows(InvalidFilenameException.class, () ->
      validator.validate(UploadCandidate.builder().filename("ab").build()));
  }

  @Test
  void aceitaNomeValido() {
    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().filename("documento.pdf").build()));
  }

}
