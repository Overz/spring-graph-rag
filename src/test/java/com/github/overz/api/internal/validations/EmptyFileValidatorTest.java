package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.EmptyFileException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmptyFileValidatorTest extends Assertions {

  private final EmptyFileValidator validator = new EmptyFileValidator();

  @Test
  void rejeitaArquivoVazio() {
    assertThrows(EmptyFileException.class, () ->
      validator.validate(UploadCandidate.builder().sizeBytes(0).build()));
  }

  @Test
  void aceitaArquivoComConteudo() {
    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().sizeBytes(1).build()));
  }

}
