package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.FileTooLargeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class FileSizeValidatorTest extends Assertions {

  private final FileSizeValidator validator = new FileSizeValidator(new UploadProperties(10, 255, Map.of()));

  @Test
  void rejeitaArquivoAcimaDoLimite() {
    assertThrows(FileTooLargeException.class, () ->
      validator.validate(UploadCandidate.builder().sizeBytes(11).build()));
  }

  @Test
  void aceitaArquivoDentroDoLimite() {
    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().sizeBytes(10).build()));
  }

}
